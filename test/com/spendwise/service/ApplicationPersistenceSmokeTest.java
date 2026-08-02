package com.spendwise.service;

import com.spendwise.config.AppPaths;
import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.CardType;
import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.DebtDirection;
import com.spendwise.repository.CsvAccountPreferenceRepository;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvCurrencyPreferenceRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvPaymentCardRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.repository.CsvBudgetPlanRepository;
import com.spendwise.repository.CsvSavingsGoalRepository;
import com.spendwise.repository.CsvDebtRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ApplicationPersistenceSmokeTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);
    private static int passed;

    private ApplicationPersistenceSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> productionBefore = fingerprint(
                AppPaths.getExpenseCsvPath().getParent());
        Path directory = Files.createTempDirectory(
                "spendwise-restart-smoke-");
        try {
            test("complete workflow reloads after restart", () ->
                completeWorkflowReloads(directory));
            test("restart and reads do not rewrite data", () ->
                restartIsReadOnly(directory));
            test("restart smoke leaves production data untouched", () ->
                assertEquals(productionBefore, fingerprint(
                        AppPaths.getExpenseCsvPath().getParent())));
        } finally {
            deleteRecursively(directory);
        }
        System.out.println("All " + passed
                + " application persistence smoke tests passed.");
    }

    private static void completeWorkflowReloads(Path directory) {
        Services first = wire(directory);
        Category travel = first.categories.addCategory("Travel");
        Account bank = first.accounts.addAccount(
                "Savings",
                AccountType.BANK,
                new BigDecimal("100.00"));
        first.accounts.setDefaultAccount(bank.getIdentifier());
        first.expenses.createExpense(
                "Train",
                new BigDecimal("12.34"),
                DATE,
                travel,
                bank,
                "Return ticket");
        first.income.createIncome(
                DATE,
                new BigDecimal("50.00"),
                "Refund",
                bank,
                "Travel refund");
        first.transfers.createTransfer(
                DATE,
                new BigDecimal("25.00"),
                Account.DEFAULT,
                bank,
                "Deposit");
        first.budgets.saveBudget(new MonthlyBudget(
                YearMonth.from(DATE),
                Optional.of(new BigDecimal("500.00")),
                Map.of(travel, new BigDecimal("100.00"))));
        first.recurring.addDefinition(
                RecurringEntryType.EXPENSE,
                new BigDecimal("9.99"),
                "Monthly pass",
                travel,
                bank,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                DATE.plusMonths(1),
                null,
                true);
        Account creditCard = first.accounts.addAccount(
                "Student Card",
                AccountType.CREDIT_CARD,
                BigDecimal.ZERO);
        first.cards.addCard(
                "Student Card",
                "Example Bank",
                CardType.CREDIT,
                "4242",
                new BigDecimal("50000.00"),
                15,
                25,
                creditCard,
                bank);
        first.currency.setCurrency("USD");
        var plan = first.advancedBudgets.addPlan("Semester budget", DATE,
                DATE.plusMonths(3), new BigDecimal("600.00"),
                Map.of(travel, new BigDecimal("150.00")),
                BudgetRolloverMode.NONE);
        var goal = first.goals.addGoal("Laptop", new BigDecimal("1000.00"),
                DATE.plusYears(1), bank);
        first.goals.addContribution(goal.getIdentifier(), DATE,
                new BigDecimal("100.00"), "Saved");
        var debt = first.debts.addDebt(DebtDirection.BORROWED, "Family",
                new BigDecimal("200.00"), DATE.plusMonths(2), "Tuition");
        first.debts.addRepayment(debt.getIdentifier(), DATE,
                new BigDecimal("20.00"), "First payment");

        Services restarted = wire(directory);
        Category loadedTravel = restarted.categories.resolveCategory(
                travel.getIdentifier());
        Account loadedBank = restarted.accounts.resolveAccount(
                bank.getIdentifier());
        assertEquals("Travel", loadedTravel.getDisplayName());
        assertEquals("Savings", loadedBank.getDisplayName());
        assertEquals(loadedBank, restarted.accounts.getDefaultAccount());
        assertEquals(1, restarted.expenses.getAllExpenses().size());
        assertEquals(loadedTravel, restarted.expenses
                .getAllExpenses().get(0).getCategory());
        assertEquals(loadedBank, restarted.expenses
                .getAllExpenses().get(0).getAccount());
        assertEquals(1, restarted.income.getAllIncome().size());
        assertEquals(1, restarted.transfers.getAllTransfers().size());
        assertEquals(1, restarted.recurring.listAll().size());
        assertEquals(1, restarted.cards.listAll().size());
        assertEquals("USD", restarted.currency.getCurrency()
                .getCurrencyCode());
        assertMoney("500.00", restarted.budgets
                .getBudget(YearMonth.from(DATE))
                .getOverallLimit().orElseThrow());
        assertMoney("100.00", restarted.budgets
                .getBudget(YearMonth.from(DATE))
                .getCategoryLimit(loadedTravel).orElseThrow());
        assertMoney("162.66", restarted.finance
                .calculateBalances().getBalance(loadedBank));
        assertMoney("137.66", restarted.finance
                .calculateBalances().getTotalBalance());
        assertEquals(1, restarted.advancedBudgets.listHistory().size());
        assertMoney("12.34", restarted.advancedBudgets.evaluate(
                plan.getIdentifier()).getOverallUsage().getSpent());
        assertMoney("100.00", restarted.goals.getProgress(
                goal.getIdentifier()).contributedAmount());
        assertMoney("180.00", restarted.debts.getProgress(
                debt.getIdentifier(), DATE).remainingAmount());

        Set<String> storedNames;
        try (var files = Files.list(directory)) {
            storedNames = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect test data.", exception);
        }
        assertEquals(Set.copyOf(ManagedDataFiles.FILE_NAMES), storedNames);
    }

    private static void restartIsReadOnly(Path directory) throws Exception {
        Map<String, String> before = fingerprint(directory);
        Services restarted = wire(directory);
        restarted.categories.listAllCategories();
        restarted.accounts.listAllAccounts();
        restarted.accounts.getDefaultAccount();
        restarted.expenses.getAllExpenses();
        restarted.income.getAllIncome();
        restarted.transfers.getAllTransfers();
        restarted.budgets.getBudget(YearMonth.from(DATE));
        restarted.recurring.listAll();
        restarted.cards.listAll();
        restarted.currency.getCurrency();
        restarted.finance.calculateBalances();
        restarted.advancedBudgets.listHistory();
        restarted.goals.listGoals();
        restarted.debts.listProgress(DATE);
        assertEquals(before, fingerprint(directory));
    }

    private static Services wire(Path directory) {
        CategoryService categories = new CategoryService(
                new CsvCategoryRepository(
                        directory.resolve("categories.csv")));
        AccountService accounts = new AccountService(
                new CsvAccountRepository(
                        directory.resolve("accounts.csv")),
                new CsvAccountPreferenceRepository(
                        directory.resolve("account-settings.csv")));
        ExpenseService expenses = new ExpenseService(
                new CsvExpenseRepository(
                        directory.resolve("expenses.csv"),
                        categories::resolveCategory,
                        accounts::resolveAccount),
                accounts);
        IncomeService income = new IncomeService(
                new CsvIncomeRepository(
                        directory.resolve("income.csv"),
                        accounts::resolveAccount),
                accounts);
        TransferService transfers = new TransferService(
                new CsvTransferRepository(
                        directory.resolve("transfers.csv"),
                        accounts::resolveAccount),
                accounts);
        BudgetService budgets = new BudgetService(
                new CsvBudgetRepository(
                        directory.resolve("budgets.csv"),
                        categories::resolveCategory));
        RecurringService recurring = new RecurringService(
                new CsvRecurringEntryRepository(
                        directory.resolve("recurring.csv"),
                        categories::resolveCategory,
                        accounts::resolveAccount),
                expenses,
                income,
                transfers,
                accounts,
                categories);
        PaymentCardService cards = new PaymentCardService(
                new CsvPaymentCardRepository(
                        directory.resolve("cards.csv"),
                        accounts::resolveAccount),
                accounts);
        CurrencyService currency = new CurrencyService(
                new CsvCurrencyPreferenceRepository(
                        directory.resolve("currency-settings.csv")));
        AdvancedBudgetService advancedBudgets = new AdvancedBudgetService(
                new CsvBudgetPlanRepository(
                        directory.resolve("budget-plans.csv"),
                        categories::resolveCategory), expenses);
        SavingsGoalService goals = new SavingsGoalService(
                new CsvSavingsGoalRepository(
                        directory.resolve("savings-goals.csv"),
                        accounts::resolveAccount), accounts);
        DebtService debts = new DebtService(new CsvDebtRepository(
                directory.resolve("debts.csv")));
        return new Services(
                categories,
                accounts,
                expenses,
                income,
                transfers,
                budgets,
                recurring,
                cards,
                currency,
                advancedBudgets,
                goals,
                debts,
                new FinanceService(
                        accounts, expenses, income, transfers));
    }

    private static Map<String, String> fingerprint(Path directory)
            throws IOException, NoSuchAlgorithmException {
        Map<String, String> result = new LinkedHashMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String name : ManagedDataFiles.FILE_NAMES) {
            Path path = directory.resolve(name);
            result.put(name, Files.exists(path)
                    ? java.util.HexFormat.of().formatHex(
                            digest.digest(Files.readAllBytes(path)))
                    : "MISSING");
            digest.reset();
        }
        return Map.copyOf(result);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void test(String name, ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Services(
            CategoryService categories,
            AccountService accounts,
            ExpenseService expenses,
            IncomeService income,
            TransferService transfers,
            BudgetService budgets,
            RecurringService recurring,
            PaymentCardService cards,
            CurrencyService currency,
            AdvancedBudgetService advancedBudgets,
            SavingsGoalService goals,
            DebtService debts,
            FinanceService finance) {
    }
}
