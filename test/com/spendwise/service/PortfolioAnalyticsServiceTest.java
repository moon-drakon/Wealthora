package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.CardType;
import com.spendwise.model.Category;
import com.spendwise.model.DebtDirection;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvBudgetPlanRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvDebtRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvPaymentCardRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public final class PortfolioAnalyticsServiceTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        new PortfolioAnalyticsServiceTest().run();
    }

    private void run() throws Exception {
        test("portfolio report uses real ledgers", this::portfolio);
        test("recurring commitments and filters", this::commitments);
        test("desktop notification aggregation", this::notifications);
        System.out.println("All " + passed
                + " portfolio analytics and notification tests passed.");
    }

    private void portfolio() throws Exception {
        withFixture(fixture -> {
            fixture.seedTransactions();
            var borrowed = fixture.debts.addDebt(DebtDirection.BORROWED,
                    "Bank", money("30"), LocalDate.of(2027, 1, 1), "");
            fixture.debts.addDebt(DebtDirection.LENT,
                    "Friend", money("40"), LocalDate.of(2027, 1, 1), "");
            assertTrue(borrowed.getIdentifier().startsWith("DEBT_"));
            fixture.advancedBudgets.addPlan("January plan",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                    money("100"), Map.of(Category.FOOD, money("50")),
                    BudgetRolloverMode.NONE);
            PortfolioAnalyticsSnapshot snapshot = fixture.portfolio.build(
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                    null, null, LocalDate.of(2025, 1, 31));
            assertMoney("130.00", snapshot.accountTotal());
            assertMoney("30.00", snapshot.outstandingBorrowed());
            assertMoney("40.00", snapshot.outstandingLent());
            assertMoney("140.00", snapshot.netWorth());
            assertMoney("50.00", snapshot.transactionReport().getTotalIncome());
            assertMoney("20.00", snapshot.transactionReport().getTotalExpenses());
            assertEquals(1, snapshot.customBudgetPerformance().size());
        });
    }

    private void commitments() throws Exception {
        withFixture(fixture -> {
            fixture.recurring.addDefinition(RecurringEntryType.INCOME,
                    money("100"), "Allowance", null, fixture.bank, null,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2025, 1, 1), null,
                    RecurringKind.SCHEDULED_TRANSACTION, 3, true);
            fixture.recurring.addDefinition(RecurringEntryType.EXPENSE,
                    money("20"), "Streaming", Category.ENTERTAINMENT,
                    fixture.bank, null, RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2025, 1, 1), null,
                    RecurringKind.SUBSCRIPTION, 3, true);
            fixture.recurring.addDefinition(RecurringEntryType.TRANSFER,
                    money("30"), "Save", null, fixture.bank, Account.DEFAULT,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2025, 1, 1), null,
                    RecurringKind.SCHEDULED_TRANSACTION, 3, true);
            RecurringCommitmentSummary commitments = fixture.portfolio.build(
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                    fixture.bank, Category.FOOD, LocalDate.of(2025, 1, 1))
                    .recurringCommitments();
            assertMoney("100.00", commitments.scheduledIncome());
            assertMoney("20.00", commitments.scheduledExpenses());
            assertMoney("30.00", commitments.scheduledTransfers());
            assertMoney("20.00", commitments.subscriptions());
        });
    }

    private void notifications() throws Exception {
        withFixture(fixture -> {
            LocalDate reference = LocalDate.of(2025, 1, 10);
            fixture.expenses.createExpense("Budget spend", money("85"),
                    LocalDate.of(2025, 1, 5), Category.FOOD,
                    fixture.bank, "");
            fixture.monthlyBudgets.saveBudget(new MonthlyBudget(
                    YearMonth.of(2025, 1),
                    java.util.Optional.of(money("100")), Map.of()));
            fixture.recurring.addDefinition(RecurringEntryType.EXPENSE,
                    money("10"), "Internet bill", Category.BILLS,
                    fixture.bank, null, RecurrenceFrequency.MONTHLY, 1,
                    reference.plusDays(2), null, RecurringKind.BILL, 3, true);
            fixture.cards.addCard("Student card", "Example Bank",
                    CardType.CREDIT, "4242", money("500"), 1, 15,
                    fixture.creditCard, fixture.bank);
            fixture.debts.addDebt(DebtDirection.BORROWED, "Family",
                    money("20"), reference.minusDays(1), "");
            FinanceNotificationService notifications =
                    new FinanceNotificationService(fixture.recurring,
                            fixture.cards, fixture.analytics,
                            fixture.monthlyBudgets, fixture.advancedBudgets,
                            fixture.debts);
            var items = notifications.listNotifications(reference);
            assertTrue(items.stream().anyMatch(item -> item.type().equals("BILL")));
            assertTrue(items.stream().anyMatch(item ->
                    item.type().equals("CREDIT_CARD")));
            assertTrue(items.stream().anyMatch(item ->
                    item.type().equals("BUDGET")));
            assertTrue(items.stream().anyMatch(item -> item.type().equals("DEBT")));
        });
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-portfolio-");
        try { action.run(new Fixture(directory)); }
        finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final class Fixture {
        private final AccountService accounts;
        private final Account bank;
        private final Account creditCard;
        private final ExpenseService expenses;
        private final IncomeService income;
        private final TransferService transfers;
        private final ExpenseAnalyticsService analytics;
        private final BudgetService monthlyBudgets;
        private final AdvancedBudgetService advancedBudgets;
        private final RecurringService recurring;
        private final DebtService debts;
        private final PaymentCardService cards;
        private final PortfolioAnalyticsService portfolio;

        Fixture(Path directory) {
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(directory.resolve("categories.csv")));
            accounts = new AccountService(new CsvAccountRepository(
                    directory.resolve("accounts.csv")));
            bank = accounts.addAccount("Bank", AccountType.BANK, money("100"));
            creditCard = accounts.addAccount("Credit card",
                    AccountType.CREDIT_CARD, money("0"));
            expenses = new ExpenseService(new CsvExpenseRepository(
                    directory.resolve("expenses.csv"), categories::resolveCategory,
                    accounts::resolveAccount), accounts);
            income = new IncomeService(new CsvIncomeRepository(
                    directory.resolve("income.csv"), accounts::resolveAccount),
                    accounts);
            transfers = new TransferService(new CsvTransferRepository(
                    directory.resolve("transfers.csv"), accounts::resolveAccount),
                    accounts);
            FinanceService finance = new FinanceService(
                    accounts, expenses, income, transfers);
            analytics = new ExpenseAnalyticsService(expenses);
            monthlyBudgets = new BudgetService(new CsvBudgetRepository(
                    directory.resolve("budgets.csv"), categories::resolveCategory));
            advancedBudgets = new AdvancedBudgetService(
                    new CsvBudgetPlanRepository(
                            directory.resolve("budget-plans.csv"),
                            categories::resolveCategory), expenses);
            recurring = new RecurringService(new CsvRecurringEntryRepository(
                    directory.resolve("recurring.csv"),
                    categories::resolveCategory, accounts::resolveAccount),
                    expenses, income, transfers, accounts, categories);
            debts = new DebtService(new CsvDebtRepository(
                    directory.resolve("debts.csv")));
            cards = new PaymentCardService(new CsvPaymentCardRepository(
                    directory.resolve("cards.csv"), accounts::resolveAccount),
                    accounts);
            FinancialReportingService reports = new FinancialReportingService(
                    expenses, income, transfers, accounts, monthlyBudgets);
            portfolio = new PortfolioAnalyticsService(reports, finance,
                    recurring, advancedBudgets, debts);
        }

        void seedTransactions() {
            income.createIncome(LocalDate.of(2025, 1, 2), money("50"),
                    "Salary", bank, "");
            expenses.createExpense("Lunch", money("20"),
                    LocalDate.of(2025, 1, 3), Category.FOOD, bank, "");
        }
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
    private void test(String name, ThrowingRunnable action) throws Exception {
        try { action.run(); passed++; }
        catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(money(expected), actual);
    }
    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }
    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }
    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface
    private interface FixtureAction { void run(Fixture fixture) throws Exception; }
}
