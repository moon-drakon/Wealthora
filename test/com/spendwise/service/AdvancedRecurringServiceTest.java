package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AdvancedRecurringServiceTest {

    private int passed;

    public static void main(String[] args) throws Exception {
        new AdvancedRecurringServiceTest().run();
    }

    private void run() throws Exception {
        test("legacy recurring migration", this::legacyMigration);
        test("bill and subscription validation", this::kindValidation);
        test("upcoming reminder window", this::upcomingReminders);
        test("duplicate-safe generation", this::duplicateSafeGeneration);
        System.out.println("All " + passed
                + " advanced recurring tests passed.");
    }

    private void legacyMigration() throws Exception {
        withFixture(fixture -> {
            Path path = fixture.directory.resolve("recurring.csv");
            Files.writeString(path, CsvRecurringEntryRepository.LEGACY_HEADER
                    + "\nRECURRING_LEGACY,EXPENSE,10.00,Rent,FOOD,"
                    + Account.DEFAULT_IDENTIFIER
                    + ",,MONTHLY,1,2025-01-01,,2025-01-01,ACTIVE\n");
            var loaded = fixture.recurring.listAll().get(0);
            assertEquals(RecurringKind.SCHEDULED_TRANSACTION,
                    loaded.getKind());
            assertEquals(3, loaded.getReminderDays());
            byte[] before = Files.readAllBytes(path);
            fixture.recurring.listAll();
            assertArrayEquals(before, Files.readAllBytes(path));
            fixture.recurring.setActive(loaded.getIdentifier(), false);
            assertTrue(Files.readString(path).startsWith(
                    CsvRecurringEntryRepository.HEADER + "\n"));
        });
    }

    private void kindValidation() throws Exception {
        withFixture(fixture -> {
            var bill = fixture.recurring.addDefinition(
                    RecurringEntryType.EXPENSE, money("25"), "Internet bill",
                    Category.BILLS, Account.DEFAULT, null,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2025, 1, 1), null,
                    RecurringKind.BILL, 5, true);
            assertEquals(RecurringKind.BILL, bill.getKind());
            expect(ValidationException.class, () ->
                    fixture.recurring.addDefinition(
                            RecurringEntryType.INCOME, money("25"), "Invalid",
                            null, Account.DEFAULT, null,
                            RecurrenceFrequency.MONTHLY, 1,
                            LocalDate.of(2025, 1, 1), null,
                            RecurringKind.SUBSCRIPTION, 5, true));
        });
    }

    private void upcomingReminders() throws Exception {
        withFixture(fixture -> {
            LocalDate reference = LocalDate.of(2026, 1, 1);
            fixture.recurring.addDefinition(
                    RecurringEntryType.EXPENSE, money("10"), "Streaming",
                    Category.ENTERTAINMENT, Account.DEFAULT, null,
                    RecurrenceFrequency.MONTHLY, 1,
                    reference.plusDays(4), null,
                    RecurringKind.SUBSCRIPTION, 5, true);
            fixture.recurring.addDefinition(
                    RecurringEntryType.EXPENSE, money("20"), "Later bill",
                    Category.BILLS, Account.DEFAULT, null,
                    RecurrenceFrequency.MONTHLY, 1,
                    reference.plusDays(10), null,
                    RecurringKind.BILL, 2, true);
            List<UpcomingRecurringItem> upcoming =
                    fixture.recurring.findUpcoming(reference, 30);
            assertEquals(1, upcoming.size());
            assertEquals("Streaming",
                    upcoming.get(0).definition().getDescription());
            assertEquals(4L, upcoming.get(0).daysUntilDue());
        });
    }

    private void duplicateSafeGeneration() throws Exception {
        withFixture(fixture -> {
            fixture.recurring.addDefinition(
                    RecurringEntryType.EXPENSE, money("10"), "Generated bill",
                    Category.BILLS, Account.DEFAULT, null,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1),
                    RecurringKind.BILL, 3, true);
            RecurringGenerationResult first = fixture.recurring
                    .generateDueEntries(LocalDate.of(2025, 1, 1));
            RecurringGenerationResult second = fixture.recurring
                    .generateDueEntries(LocalDate.of(2025, 1, 1));
            assertEquals(1, first.generatedCount());
            assertEquals(0, second.generatedCount());
            assertEquals(1, fixture.expenses.getAllExpenses().size());
        });
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-recurring-v2-");
        try {
            action.run(new Fixture(directory));
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final class Fixture {
        private final Path directory;
        private final ExpenseService expenses;
        private final RecurringService recurring;

        Fixture(Path directory) {
            this.directory = directory;
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(directory.resolve("categories.csv")));
            AccountService accounts = new AccountService(
                    new CsvAccountRepository(directory.resolve("accounts.csv")));
            expenses = new ExpenseService(new CsvExpenseRepository(
                    directory.resolve("expenses.csv"),
                    categories::resolveCategory, accounts::resolveAccount), accounts);
            IncomeService income = new IncomeService(new CsvIncomeRepository(
                    directory.resolve("income.csv"), accounts::resolveAccount),
                    accounts);
            TransferService transfers = new TransferService(
                    new CsvTransferRepository(directory.resolve("transfers.csv"),
                            accounts::resolveAccount), accounts);
            recurring = new RecurringService(new CsvRecurringEntryRepository(
                    directory.resolve("recurring.csv"),
                    categories::resolveCategory, accounts::resolveAccount),
                    expenses, income, transfers, accounts, categories);
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

    private static void expect(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected exception.", failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
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

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface
    private interface FixtureAction { void run(Fixture fixture) throws Exception; }
}
