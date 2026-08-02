package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.BudgetPlan;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvBudgetPlanRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdvancedBudgetServiceTest {

    private int passed;

    public static void main(String[] args) throws Exception {
        new AdvancedBudgetServiceTest().run();
    }

    private void run() throws Exception {
        test("custom period persistence", this::customPeriodPersistence);
        test("progress and alert states", this::progressAndAlerts);
        test("rollover uses prior unused amount", this::rollover);
        test("overlap and invalid period validation", this::validation);
        test("monthly budget history", this::monthlyHistory);
        System.out.println("All " + passed
                + " advanced budget tests passed.");
    }

    private void customPeriodPersistence() throws Exception {
        withDirectory(directory -> {
            Fixture fixture = fixture(directory);
            BudgetPlan plan = fixture.service.addPlan(
                    "Semester living", LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 12, 31), money("5000"),
                    Map.of(Category.FOOD, money("1200")),
                    BudgetRolloverMode.NONE);
            byte[] beforeRead = Files.readAllBytes(
                    directory.resolve("budget-plans.csv"));
            Fixture restarted = fixture(directory);
            BudgetPlan loaded = restarted.service.listHistory().get(0);
            assertEquals(plan.getIdentifier(), loaded.getIdentifier());
            assertEquals(money("1200"), loaded.getCategoryLimit(
                    Category.FOOD).orElseThrow());
            assertArrayEquals(beforeRead, Files.readAllBytes(
                    directory.resolve("budget-plans.csv")));
            restarted.service.setActive(plan.getIdentifier(), false);
            assertTrue(!restarted.service.listHistory().get(0).isActive());
        });
    }

    private void progressAndAlerts() throws Exception {
        withDirectory(directory -> {
            Fixture fixture = fixture(directory);
            BudgetPlan plan = fixture.service.addPlan(
                    "August", LocalDate.of(2025, 8, 1),
                    LocalDate.of(2025, 8, 31), money("100"),
                    Map.of(Category.FOOD, money("50")),
                    BudgetRolloverMode.NONE);
            fixture.expenses.createExpense("Food", money("45"),
                    LocalDate.of(2025, 8, 2), Category.FOOD, Account.DEFAULT, "");
            fixture.expenses.createExpense("Bus", money("40"),
                    LocalDate.of(2025, 8, 3), Category.TRANSPORT,
                    Account.DEFAULT, "");
            BudgetPlanStatus status = fixture.service.evaluate(
                    plan.getIdentifier());
            assertMoney("85.00", status.getOverallUsage().getSpent());
            assertEquals(BudgetAlertLevel.NEAR_LIMIT,
                    status.getOverallUsage().getAlertLevel());
            assertEquals(BudgetAlertLevel.NEAR_LIMIT,
                    status.getCategoryUsage().get(Category.FOOD)
                            .getAlertLevel());
            fixture.expenses.createExpense("Dinner", money("10"),
                    LocalDate.of(2025, 8, 4), Category.FOOD, Account.DEFAULT, "");
            assertEquals(BudgetAlertLevel.OVER_LIMIT,
                    fixture.service.evaluate(plan.getIdentifier())
                            .getCategoryUsage().get(Category.FOOD)
                            .getAlertLevel());
        });
    }

    private void rollover() throws Exception {
        withDirectory(directory -> {
            Fixture fixture = fixture(directory);
            BudgetPlan first = fixture.service.addPlan(
                    "Living", LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 31), money("100"),
                    Map.of(Category.FOOD, money("60")),
                    BudgetRolloverMode.NONE);
            fixture.expenses.createExpense("July food", money("40"),
                    LocalDate.of(2026, 7, 5), Category.FOOD,
                    Account.DEFAULT, "");
            BudgetPlan second = fixture.service.addPlan(
                    "Living", LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 31), money("100"),
                    Map.of(Category.FOOD, money("60")),
                    BudgetRolloverMode.CARRY_UNUSED);
            BudgetPlanStatus status = fixture.service.evaluate(
                    second.getIdentifier());
            assertMoney("60.00", status.getOverallRollover());
            assertMoney("160.00", status.getOverallUsage()
                    .getLimit().orElseThrow());
            assertMoney("20.00", status.getCategoryRollover()
                    .get(Category.FOOD));
            assertTrue(fixture.service.evaluate(first.getIdentifier())
                    .getOverallRollover().signum() == 0);
        });
    }

    private void validation() throws Exception {
        withDirectory(directory -> {
            Fixture fixture = fixture(directory);
            fixture.service.addPlan("Travel",
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10),
                    money("100"), Map.of(), BudgetRolloverMode.NONE);
            expect(ValidationException.class, () -> fixture.service.addPlan(
                    "Travel", LocalDate.of(2026, 8, 5),
                    LocalDate.of(2026, 8, 20), money("100"), Map.of(),
                    BudgetRolloverMode.NONE));
            expect(ValidationException.class, () -> fixture.service.addPlan(
                    "Bad", LocalDate.of(2026, 9, 2),
                    LocalDate.of(2026, 9, 1), money("100"), Map.of(),
                    BudgetRolloverMode.NONE));
        });
    }

    private void monthlyHistory() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = new CsvBudgetRepository(
                    directory.resolve("budgets.csv"));
            BudgetService service = new BudgetService(repository);
            service.saveBudget(new MonthlyBudget(YearMonth.of(2026, 7),
                    java.util.Optional.of(money("100")), Map.of()));
            service.saveBudget(new MonthlyBudget(YearMonth.of(2026, 8),
                    java.util.Optional.of(money("200")), Map.of()));
            assertEquals(List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 7)),
                    service.listBudgetHistory().stream()
                            .map(MonthlyBudget::getMonth).toList());
        });
    }

    private static Fixture fixture(Path directory) {
        AccountService accounts = new AccountService(
                new CsvAccountRepository(directory.resolve("accounts.csv")));
        ExpenseService expenses = new ExpenseService(
                new CsvExpenseRepository(directory.resolve("expenses.csv"),
                        Category::valueOf, accounts::resolveAccount), accounts);
        AdvancedBudgetService service = new AdvancedBudgetService(
                new CsvBudgetPlanRepository(
                        directory.resolve("budget-plans.csv"), Category::valueOf),
                expenses);
        return new Fixture(expenses, service);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private void test(String name, ThrowingRunnable action) throws Exception {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void withDirectory(DirectoryAction action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-budget-plan-");
        try {
            action.run(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void expect(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Unexpected exception.", failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(money(expected), actual);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError("Expected equal bytes.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    private record Fixture(
            ExpenseService expenses, AdvancedBudgetService service) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface
    private interface DirectoryAction { void run(Path path) throws Exception; }
}
