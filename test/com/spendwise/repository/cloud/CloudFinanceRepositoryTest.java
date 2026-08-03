package com.spendwise.repository.cloud;

import com.spendwise.auth.CloudConnectionState;
import com.spendwise.auth.registration.FinanceApiGateway;
import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.BudgetPlan;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.Category;
import com.spendwise.model.DebtDirection;
import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import com.spendwise.model.Expense;
import com.spendwise.model.GoalContribution;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import com.spendwise.model.SavingsGoal;
import com.spendwise.service.MigrationPreviewService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CloudFinanceRepositoryTest {

    private static int passed;

    private CloudFinanceRepositoryTest() {
    }

    public static void main(String[] args) throws Exception {
        test("cloud repositories page and restore server models",
                CloudFinanceRepositoryTest::restoreModels);
        test("cloud writes never send an owner identifier",
                CloudFinanceRepositoryTest::ownerComesOnlyFromSession);
        test("planning repositories use authenticated finance routes",
                CloudFinanceRepositoryTest::planningRoutes);
        test("migration preview is read-only",
                CloudFinanceRepositoryTest::migrationPreviewIsReadOnly);
        System.out.println("All " + passed
                + " cloud finance repository tests passed.");
    }

    private static void restoreModels() {
        RecordingGateway gateway = new RecordingGateway();
        CloudFinanceRepositories repositories = repositories(gateway);
        List<Account> accounts = repositories.accounts().findAll();
        assertEquals(1, accounts.size());
        assertEquals("ACCOUNT_BANK", accounts.get(0).getIdentifier());
        assertTrue(gateway.requests.stream().anyMatch(value ->
                value.path().contains("page=1")));

        List<Category> categories = repositories.categories().findAll();
        assertEquals(1, categories.size());
        assertEquals("CUSTOM_BOOKS", categories.get(0).getIdentifier());
        List<Expense> expenses = repositories.expenses().findAll();
        assertEquals(1, expenses.size());
        assertEquals("ACCOUNT_BANK",
                expenses.get(0).getAccount().getIdentifier());
        assertEquals(Category.EDUCATION, expenses.get(0).getCategory());
        assertEquals(PaymentMethod.DEBIT_CARD,
                expenses.get(0).getPaymentMethod());
        assertEquals(CloudConnectionState.CONNECTED,
                gateway.getCloudConnectionState());
    }

    private static void ownerComesOnlyFromSession() {
        RecordingGateway gateway = new RecordingGateway();
        CloudFinanceRepositories repositories = repositories(gateway);
        Account account = Account.createCustom("ACCOUNT_SECOND", "Second",
                AccountType.BANK, new BigDecimal("25.00"), "bank",
                "#1F7E60", "BDT", "NSU Bank",
                LocalDate.of(2026, 8, 1), false);
        repositories.accounts().add(account);
        Expense expense = new Expense("expense-cloud-001", "Lunch",
                new BigDecimal("10.00"), LocalDate.of(2026, 8, 3),
                Category.FOOD, account, PaymentMethod.CASH,
                List.of("meal"), "");
        repositories.expenses().add(expense);
        Request accountRequest = gateway.last("POST", "/api/finance/accounts");
        Request expenseRequest = gateway.last("POST", "/api/finance/expenses");
        assertFalse(accountRequest.body().toLowerCase().contains("owner"));
        assertFalse(accountRequest.body().toLowerCase().contains("userid"));
        assertFalse(expenseRequest.body().toLowerCase().contains("owner"));
        assertFalse(expenseRequest.body().toLowerCase().contains("userid"));
        assertTrue(expenseRequest.body().contains("ACCOUNT_SECOND"));
    }

    private static void planningRoutes() {
        RecordingGateway gateway = new RecordingGateway();
        CloudFinanceRepositories repositories = repositories(gateway);
        Account account = repositories.accounts().findAll().get(0);
        MonthlyBudget monthly = new MonthlyBudget(YearMonth.of(2026, 8),
                Optional.of(new BigDecimal("1000.00")),
                Map.of(Category.FOOD, new BigDecimal("300.00")));
        repositories.budgets().save(monthly);

        BudgetPlan plan = new BudgetPlan("BUDGET_TERM", "Term",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("5000.00"), Map.of(Category.EDUCATION,
                        new BigDecimal("2000.00")),
                BudgetRolloverMode.CARRY_UNUSED, true);
        repositories.budgetPlans().add(plan);
        RecurringEntry recurring = new RecurringEntry("RECURRING_RENT",
                RecurringEntryType.EXPENSE, new BigDecimal("100.00"),
                "Rent", Category.BILLS, account, null,
                RecurrenceFrequency.MONTHLY, 1,
                LocalDate.of(2026, 8, 1), null,
                LocalDate.of(2026, 9, 1), RecurringKind.BILL, 3, true);
        repositories.recurring().add(recurring);
        SavingsGoal goal = new SavingsGoal("GOAL_LAPTOP", "Laptop",
                new BigDecimal("1500.00"), LocalDate.of(2026, 12, 1),
                account, true);
        repositories.goals().addGoal(goal);
        repositories.goals().addContribution(new GoalContribution(
                "CONTRIBUTION_ONE", goal.getIdentifier(),
                LocalDate.of(2026, 8, 3), new BigDecimal("100.00"), ""));
        DebtRecord debt = new DebtRecord("DEBT_BOOKS",
                DebtDirection.BORROWED, "Family", new BigDecimal("300.00"),
                LocalDate.of(2026, 12, 31), "");
        repositories.debts().addDebt(debt);
        repositories.debts().addRepayment(new DebtRepayment("REPAYMENT_ONE",
                debt.getIdentifier(), LocalDate.of(2026, 8, 3),
                new BigDecimal("50.00"), ""));

        assertTrue(gateway.hasPath("PUT",
                "/api/finance/budgets/monthly/2026-08"));
        assertTrue(gateway.hasPath("POST", "/api/finance/budgets/plans"));
        assertTrue(gateway.hasPath("POST", "/api/finance/recurring"));
        assertTrue(gateway.hasPath("POST", "/api/finance/goals"));
        assertTrue(gateway.hasPath("POST",
                "/api/finance/goals/GOAL_LAPTOP/contributions"));
        assertTrue(gateway.hasPath("POST", "/api/finance/debts"));
        assertTrue(gateway.hasPath("POST",
                "/api/finance/debts/DEBT_BOOKS/repayments"));
    }

    private static void migrationPreviewIsReadOnly() throws Exception {
        Path directory = Files.createTempDirectory("wealthora-preview-")
                .toAbsolutePath().normalize();
        try {
            Path expense = directory.resolve("expenses.csv");
            Path income = directory.resolve("income.csv");
            Files.writeString(expense, "id,amount\n1,25\n");
            Files.writeString(income, "id,amount\n2,50\n");
            String beforeExpense = sha256(expense);
            String beforeIncome = sha256(income);
            MigrationPreviewService.MigrationPreview preview =
                    new MigrationPreviewService(directory).preview();
            assertEquals(2, preview.files().size());
            assertTrue(preview.displayText().contains("nothing will be uploaded"));
            assertTrue(preview.displayText().contains(
                    "Automatic migration and synchronization are not enabled"));
            assertEquals(beforeExpense, sha256(expense));
            assertEquals(beforeIncome, sha256(income));
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    private static CloudFinanceRepositories repositories(
            RecordingGateway gateway) {
        return new CloudFinanceRepositories(new CloudFinanceClient(gateway));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
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

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false.");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected <" + expected
                    + "> but was <" + actual + ">.");
        }
    }

    private static final class RecordingGateway implements FinanceApiGateway {
        private final List<Request> requests = new ArrayList<>();

        @Override
        public String requestFinance(String method, String path, String body) {
            requests.add(new Request(method, path, body));
            String route = path.split("\\?", 2)[0];
            if (!"GET".equals(method)) return "{}";
            if (route.equals("/api/finance/accounts")) {
                if (path.contains("page=1")) return page("""
                        {"externalId":"ACCOUNT_BANK","name":"Bank",
                        "accountType":"BANK","currencyCode":"BDT",
                        "openingBalance":100.00,"currentBalance":100.00,
                        "iconName":"bank","colorHex":"#1F7E60",
                        "institutionName":"NSU Bank","openedOn":"2026-08-01",
                        "archived":false,"defaultAccount":false}
                        """, 1, 2, 2);
                return page("""
                        {"externalId":"ACCOUNT_DEFAULT_CASH","name":"Cash",
                        "accountType":"CASH","currencyCode":"BDT",
                        "openingBalance":0.00,"currentBalance":0.00,
                        "iconName":"cash","colorHex":"#1F7E60",
                        "institutionName":"","openedOn":null,
                        "archived":false,"defaultAccount":true}
                        """, 0, 2, 2);
            }
            if (route.equals("/api/finance/categories")) return page("""
                    {"externalId":"FOOD","name":"Food","builtIn":true,
                    "archived":false,"parentExternalId":null},
                    {"externalId":"CUSTOM_BOOKS","name":"Books",
                    "builtIn":false,"archived":false,
                    "parentExternalId":"EDUCATION"}
                    """, 0, 2, 1);
            if (route.equals("/api/finance/expenses")) return page("""
                    {"externalId":"expense-cloud-001","transactionType":"EXPENSE",
                    "description":"Books","amount":25.00,"date":"2026-08-03",
                    "accountExternalId":"ACCOUNT_BANK",
                    "categoryExternalId":"EDUCATION",
                    "paymentMethod":"DEBIT_CARD","tags":["study"],
                    "note":"","transferExternalId":null,
                    "transferDirection":null}
                    """, 0, 1, 1);
            return page("", 0, 0, 0);
        }

        @Override
        public CloudConnectionState getCloudConnectionState() {
            return CloudConnectionState.CONNECTED;
        }

        private boolean hasPath(String method, String path) {
            return requests.stream().anyMatch(value -> value.method().equals(method)
                    && value.path().equals(path));
        }

        private Request last(String method, String path) {
            return requests.stream().filter(value -> value.method().equals(method)
                    && value.path().equals(path)).reduce((left, right) -> right)
                    .orElseThrow();
        }

        private static String page(
                String content, int page, int totalElements, int totalPages) {
            return "{\"content\":[" + content + "],\"page\":" + page
                    + ",\"size\":100,\"totalElements\":" + totalElements
                    + ",\"totalPages\":" + totalPages + "}";
        }
    }

    private record Request(String method, String path, String body) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
