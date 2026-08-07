package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ExportServiceTest {

    private static int passed;

    private ExportServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        test("expense export headers and escaping", ExportServiceTest::expenseExport);
        test("income export exact money", ExportServiceTest::incomeExport);
        test("Exportable transaction CSV", ExportServiceTest::transactionExport);
        test("transfer export fields", ExportServiceTest::transferExport);
        test("account summary export", ExportServiceTest::accountExport);
        test("date-range report export", ExportServiceTest::reportExport);
        test("filtered report export", ExportServiceTest::filteredReport);
        test("report budget export", ExportServiceTest::budgetReport);
        test("existing export requires confirmation", ExportServiceTest::existingDestination);
        test("confirmed export replacement", ExportServiceTest::confirmedReplacement);
        test("exports never mutate repositories", ExportServiceTest::readOnly);
        System.out.println("All " + passed + " export tests passed.");
    }

    private static void expenseExport() throws Exception {
        withFixture(fixture -> {
            Path target = fixture.root.resolve("expenses.csv");
            ExportResult result = fixture.export.exportExpenses(target, false);
            String text = Files.readString(target, StandardCharsets.UTF_8);
            assertTrue(text.startsWith(
                    "id,date,description,amount,categoryId,categoryName,"
                    + "accountId,accountName,notes\n"));
            assertContains(text, "\"Lunch, cafe\"");
            assertContains(text, "12.50");
            assertContains(text, "\"line one\nline two\"");
            assertEquals(1, result.rowCount());
        });
    }

    private static void incomeExport() throws Exception {
        withFixture(fixture -> {
            Path target = fixture.root.resolve("income-export.csv");
            fixture.export.exportIncome(target, false);
            String text = Files.readString(target);
            assertTrue(text.startsWith(
                    "id,date,amount,source,accountId,accountName,note\n"));
            assertContains(text, "100.00");
            assertContains(text, "\"Salary, August\"");
        });
    }

    private static void transactionExport() throws Exception {
        withFixture(fixture -> {
            assertTrue(fixture.export instanceof Exportable);
            String generated = fixture.export.generateCSV();
            assertTrue(generated.startsWith(
                    "id,type,date,description,amount,impact,category,account,note\n"));
            assertContains(generated, "Income,2024-08-01");
            assertContains(generated, "Expense,2024-08-05");
            assertContains(generated, ",-12.50,Food,");

            Path target = fixture.root.resolve("transactions-export.csv");
            ExportResult result = fixture.export.exportTransactions(
                    target, false);
            assertEquals(generated, Files.readString(target));
            assertEquals(2, result.rowCount());
        });
    }

    private static void transferExport() throws Exception {
        withFixture(fixture -> {
            Path target = fixture.root.resolve("transfers-export.csv");
            fixture.export.exportTransfers(target, false);
            String text = Files.readString(target);
            assertTrue(text.startsWith(
                    "id,date,amount,sourceAccountId,sourceAccountName,"
                    + "destinationAccountId,destinationAccountName,note\n"));
            assertContains(text, "20.00");
            assertContains(text, fixture.bank.getIdentifier());
        });
    }

    private static void accountExport() throws Exception {
        withFixture(fixture -> {
            Path target = fixture.root.resolve("accounts-export.csv");
            ExportResult result = fixture.export.exportAccountSummary(
                    target, false);
            String text = Files.readString(target);
            assertTrue(text.startsWith(
                    "id,name,type,status,openingBalance,currentBalance\n"));
            assertContains(text, "117.50");
            assertEquals(2, result.rowCount());
        });
    }

    private static void reportExport() throws Exception {
        withFixture(fixture -> {
            AdvancedReportSnapshot report = fixture.reporting
                    .buildAdvancedReport(
                            LocalDate.of(2024, 8, 1),
                            LocalDate.of(2024, 8, 31),
                            null,
                            null);
            Path target = fixture.root.resolve("report.csv");
            fixture.export.exportReport(target, false, report);
            String text = Files.readString(target);
            assertTrue(text.startsWith(
                    "recordType,period,label,income,expenses,incomingTransfers,"
                    + "outgoingTransfers,net,limit,actual,remaining\n"));
            assertContains(text, "SUMMARY");
            assertContains(text, "100.00,12.50");
            assertContains(text, "MONTH,2024-08,Cash flow");
        });
    }

    private static void filteredReport() throws Exception {
        withFixture(fixture -> {
            AdvancedReportSnapshot report = fixture.reporting
                    .buildAdvancedReport(
                            LocalDate.of(2024, 8, 1),
                            LocalDate.of(2024, 8, 31),
                            fixture.bank,
                            Category.FOOD);
            Path target = fixture.root.resolve("filtered-report.csv");
            fixture.export.exportReport(target, false, report);
            String text = Files.readString(target);
            assertContains(text, "EXPENSE_CATEGORY,,Food,,12.50");
            assertContains(text, "INCOME_SOURCE,,\"Salary, August\",100.00");
            assertFalse(text.contains("Bills"));
        });
    }

    private static void budgetReport() throws Exception {
        withFixture(fixture -> {
            AdvancedReportSnapshot report = fixture.reporting
                    .buildAdvancedReport(
                            LocalDate.of(2024, 8, 1),
                            LocalDate.of(2024, 8, 31),
                            null,
                            null);
            Path target = fixture.root.resolve("budget-report.csv");
            fixture.export.exportReport(target, false, report);
            String text = Files.readString(target);
            assertContains(text, "BUDGET,2024-08,Overall");
            assertContains(text, "200.00,12.50,187.50");
            assertContains(text, "BUDGET_CATEGORY,2024-08,Food");
        });
    }

    private static void existingDestination() throws Exception {
        withFixture(fixture -> {
            Path target = fixture.root.resolve("existing.csv");
            Files.writeString(target, "keep");
            expect(ValidationException.class,
                    () -> fixture.export.exportExpenses(target, false));
            assertEquals("keep", Files.readString(target));
        });
    }

    private static void confirmedReplacement() throws Exception {
        withFixture(fixture -> {
            Path target = fixture.root.resolve("existing.csv");
            Files.writeString(target, "replace");
            fixture.export.exportExpenses(target, true);
            assertTrue(Files.readString(target).startsWith("id,date"));
        });
    }

    private static void readOnly() throws Exception {
        withFixture(fixture -> {
            Map<String, byte[]> before = fixture.dataBytes();
            fixture.export.exportExpenses(fixture.root.resolve("e.csv"), false);
            fixture.export.exportIncome(fixture.root.resolve("i.csv"), false);
            fixture.export.exportTransactions(
                    fixture.root.resolve("transactions.csv"), false);
            fixture.export.exportTransfers(fixture.root.resolve("t.csv"), false);
            fixture.export.exportAccountSummary(
                    fixture.root.resolve("a.csv"), false);
            AdvancedReportSnapshot report = fixture.reporting
                    .buildAdvancedReport(
                            LocalDate.of(2024, 8, 1),
                            LocalDate.of(2024, 8, 31),
                            null,
                            null);
            fixture.export.exportReport(
                    fixture.root.resolve("r.csv"), false, report);
            assertFileBytes(before, fixture.dataBytes());
        });
    }

    private static final class Fixture {

        private final Path root;
        private final Path data;
        private final Account bank;
        private final FinancialReportingService reporting;
        private final ExportService export;

        private Fixture(Path root) {
            this.root = root;
            data = root.resolve("data");
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(data.resolve("categories.csv")));
            AccountService accounts = new AccountService(
                    new CsvAccountRepository(data.resolve("accounts.csv")));
            bank = accounts.addAccount(
                    "Export Bank",
                    AccountType.BANK,
                    new BigDecimal("10.00"));
            ExpenseService expenses = new ExpenseService(
                    new CsvExpenseRepository(
                            data.resolve("expenses.csv"),
                            categories::resolveCategory,
                            accounts::resolveAccount),
                    accounts);
            IncomeService income = new IncomeService(
                    new CsvIncomeRepository(
                            data.resolve("income.csv"),
                            accounts::resolveAccount),
                    accounts);
            TransferService transfers = new TransferService(
                    new CsvTransferRepository(
                            data.resolve("transfers.csv"),
                            accounts::resolveAccount),
                    accounts);
            BudgetService budgets = new BudgetService(
                    new CsvBudgetRepository(
                            data.resolve("budgets.csv"),
                            categories::resolveCategory));
            expenses.createExpense(
                    "Lunch, cafe",
                    new BigDecimal("12.50"),
                    LocalDate.of(2024, 8, 5),
                    Category.FOOD,
                    bank,
                    "line one\nline two");
            income.createIncome(
                    LocalDate.of(2024, 8, 1),
                    new BigDecimal("100.00"),
                    "Salary, August",
                    bank,
                    "");
            transfers.createTransfer(
                    LocalDate.of(2024, 8, 2),
                    new BigDecimal("20.00"),
                    Account.DEFAULT,
                    bank,
                    "Savings");
            budgets.saveBudget(new MonthlyBudget(
                    YearMonth.of(2024, 8),
                    Optional.of(new BigDecimal("200.00")),
                    Map.of(Category.FOOD, new BigDecimal("50.00"))));
            FinanceService finance = new FinanceService(
                    accounts, expenses, income, transfers);
            reporting = new FinancialReportingService(
                    expenses, income, transfers, accounts, budgets);
            export = new ExportService(
                    expenses, income, transfers, accounts, finance);
        }

        private Map<String, byte[]> dataBytes() throws IOException {
            LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
            try (var paths = Files.list(data)) {
                for (Path path : paths.sorted().toList()) {
                    if (Files.isRegularFile(path)) {
                        result.put(path.getFileName().toString(),
                                Files.readAllBytes(path));
                    }
                }
            }
            return result;
        }
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path root = Files.createTempDirectory("spendwise-export-test-");
        try {
            action.run(new Fixture(root));
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void assertFileBytes(
            Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String name : expected.keySet()) {
            if (!Arrays.equals(expected.get(name), actual.get(name))) {
                throw new AssertionError("Managed file changed: " + name);
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

    private static <T extends Throwable> void expect(
            Class<T> expected, ThrowingRunnable action) throws Exception {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(
                    "Expected " + expected.getSimpleName()
                    + " but caught " + actual, actual);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + ".");
    }

    private static void assertContains(String actual, String part) {
        if (!actual.contains(part)) {
            throw new AssertionError(
                    "Expected <" + actual + "> to contain <" + part + ">.");
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean value) {
        assertTrue(!value);
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

    @FunctionalInterface
    private interface FixtureAction {

        void run(Fixture fixture) throws Exception;
    }
}
