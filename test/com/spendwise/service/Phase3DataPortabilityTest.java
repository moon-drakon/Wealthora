package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Phase3DataPortabilityTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        new Phase3DataPortabilityTest().run();
    }

    private void run() throws Exception {
        test("versioned JSON round trip", this::jsonRoundTrip);
        test("JSON checksum validation", this::jsonChecksum);
        test("CSV import and safety backup", this::csvImport);
        test("CSV validation precedes mutation", this::csvValidation);
        test("dependency-free PDF and overwrite safety", this::pdfExport);
        System.out.println("All " + passed
                + " Phase 3 data portability tests passed.");
    }

    private void jsonRoundTrip() throws Exception {
        withFixture(fixture -> {
            fixture.expenses.createExpense("Original", money("12"),
                    LocalDate.of(2025, 1, 2), Category.FOOD,
                    Account.DEFAULT, "");
            byte[] expected = Files.readAllBytes(
                    fixture.data.resolve("expenses.csv"));
            Path backup = fixture.root.resolve("backup.json");
            BackupResult result = fixture.json.createBackup(backup, false);
            assertTrue(result.includedFiles().contains("expenses.csv"));
            assertTrue(Files.readString(backup).contains("\"formatVersion\":1"));
            fixture.expenses.createExpense("Later", money("3"),
                    LocalDate.of(2025, 1, 3), Category.FOOD,
                    Account.DEFAULT, "");
            RestoreResult restore = fixture.json.restoreBackup(backup);
            assertTrue(restore.restoredFiles().contains("expenses.csv"));
            assertArrayEquals(expected,
                    Files.readAllBytes(fixture.data.resolve("expenses.csv")));
        });
    }

    private void jsonChecksum() throws Exception {
        withFixture(fixture -> {
            fixture.expenses.createExpense("Checksum", money("1"),
                    LocalDate.of(2025, 1, 1), Category.FOOD,
                    Account.DEFAULT, "");
            Path backup = fixture.root.resolve("backup.json");
            fixture.json.createBackup(backup, false);
            String text = Files.readString(backup);
            int content = text.indexOf("contentBase64\":\"");
            if (content < 0) throw new AssertionError("Expected backup content.");
            int index = content + "contentBase64\":\"".length();
            char replacement = text.charAt(index) == 'A' ? 'B' : 'A';
            Files.writeString(backup,
                    text.substring(0, index) + replacement
                    + text.substring(index + 1));
            expect(ValidationException.class,
                    () -> fixture.json.inspectBackup(backup));
        });
    }

    private void csvImport() throws Exception {
        withFixture(fixture -> {
            Path source = fixture.root.resolve("expense-import.csv");
            Files.writeString(source,
                    "id,date,description,amount,categoryId,categoryName,"
                    + "accountId,accountName,notes\n"
                    + "IMPORTED_1,2025-02-01,Lunch,15.75,FOOD,Food,"
                    + "ACCOUNT_DEFAULT_CASH,Cash,Imported note\n",
                    StandardCharsets.UTF_8);
            ImportResult result = fixture.csv.importFile(source);
            assertEquals(1, result.importedCount());
            assertTrue(Files.isRegularFile(result.safetyBackup()));
            assertEquals("Imported note", fixture.expenses
                    .findExpenseById("IMPORTED_1").orElseThrow().getNotes());
        });
    }

    private void csvValidation() throws Exception {
        withFixture(fixture -> {
            Path source = fixture.root.resolve("invalid-import.csv");
            Files.writeString(source,
                    "id,date,description,amount,categoryId,categoryName,"
                    + "accountId,accountName,notes\n"
                    + "BROKEN,2025-02-01,Lunch,-2.00,FOOD,Food,"
                    + "ACCOUNT_DEFAULT_CASH,Cash,\n");
            expect(ValidationException.class,
                    () -> fixture.csv.importFile(source));
            assertTrue(fixture.expenses.getAllExpenses().isEmpty());
            try (var paths = Files.list(fixture.root)) {
                assertTrue(paths.noneMatch(path -> path.getFileName().toString()
                        .startsWith("SpendWise-import-safety-")));
            }
        });
    }

    private void pdfExport() throws Exception {
        withFixture(fixture -> {
            AdvancedReportSnapshot report = new AdvancedReportSnapshot(
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                    money("100"), money("40"), Map.of(), Map.of(),
                    Map.of(), Map.of(), List.of(), List.of());
            PortfolioAnalyticsSnapshot portfolio =
                    new PortfolioAnalyticsSnapshot(report,
                            new LinkedHashMap<>(), money("60"), money("0"),
                            money("0"), money("60"),
                            new RecurringCommitmentSummary(money("0"),
                                    money("0"), money("0"), money("0"),
                                    money("0")), List.of());
            Path target = fixture.root.resolve("report.pdf");
            ExportResult result = new PdfReportService().export(
                    target, false, portfolio, "BDT");
            assertTrue(result.rowCount() > 0);
            byte[] content = Files.readAllBytes(target);
            assertTrue(new String(content, 0, 8, StandardCharsets.US_ASCII)
                    .startsWith("%PDF-1.4"));
            expect(ValidationException.class, () -> new PdfReportService()
                    .export(target, false, portfolio, "BDT"));
        });
    }

    private static final class Fixture {
        private final Path root;
        private final Path data;
        private final ExpenseService expenses;
        private final JsonBackupService json;
        private final CsvImportService csv;

        Fixture(Path root) {
            this.root = root;
            this.data = root.resolve("data");
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(data.resolve("categories.csv")));
            AccountService accounts = new AccountService(
                    new CsvAccountRepository(data.resolve("accounts.csv")));
            expenses = new ExpenseService(new CsvExpenseRepository(
                    data.resolve("expenses.csv"), categories::resolveCategory,
                    accounts::resolveAccount), accounts);
            IncomeService income = new IncomeService(new CsvIncomeRepository(
                    data.resolve("income.csv"), accounts::resolveAccount),
                    accounts);
            TransferService transfers = new TransferService(
                    new CsvTransferRepository(data.resolve("transfers.csv"),
                            accounts::resolveAccount), accounts);
            BackupService backups = new BackupService(data);
            json = new JsonBackupService(data);
            csv = new CsvImportService(data, backups, expenses, income,
                    transfers, accounts, categories);
            categories.listAllCategories();
            accounts.listAllAccounts();
        }
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path root = Files.createTempDirectory("spendwise-portability-");
        try { action.run(new Fixture(root)); }
        finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
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
    private static <T extends Throwable> void expect(
            Class<T> expected, ThrowingRunnable action) throws Exception {
        try { action.run(); }
        catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError("Expected " + expected.getSimpleName()
                    + " but caught " + actual, actual);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + ".");
    }
    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError("Byte arrays differ.");
        }
    }
    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }
    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(
                "Expected <" + expected + "> but was <" + actual + ">.");
    }
    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface
    private interface FixtureAction { void run(Fixture fixture) throws Exception; }
}
