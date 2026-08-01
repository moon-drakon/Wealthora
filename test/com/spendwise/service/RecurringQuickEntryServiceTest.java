package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class RecurringQuickEntryServiceTest {

    private static int passed;

    private RecurringQuickEntryServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        test("due definitions are selected", RecurringQuickEntryServiceTest::dueSelection);
        test("daily occurrences generate", RecurringQuickEntryServiceTest::dailyGeneration);
        test("weekly interval greater than one", RecurringQuickEntryServiceTest::weeklyInterval);
        test("monthly month-end generation", RecurringQuickEntryServiceTest::monthlyGeneration);
        test("yearly leap generation", RecurringQuickEntryServiceTest::yearlyGeneration);
        test("optional end date stops generation", RecurringQuickEntryServiceTest::endDate);
        test("inactive definitions do not generate", RecurringQuickEntryServiceTest::inactive);
        test("duplicate occurrence recovery", RecurringQuickEntryServiceTest::duplicateRecovery);
        test("recurring expense fields", RecurringQuickEntryServiceTest::expenseFields);
        test("recurring income fields", RecurringQuickEntryServiceTest::incomeFields);
        test("recurring transfer fields", RecurringQuickEntryServiceTest::transferFields);
        test("archived account blocks generation", RecurringQuickEntryServiceTest::archivedAccount);
        test("definition update preserves ID", RecurringQuickEntryServiceTest::stableUpdate);
        test("definition activation toggle", RecurringQuickEntryServiceTest::activation);
        test("quick expense integration", RecurringQuickEntryServiceTest::quickExpense);
        test("quick income integration", RecurringQuickEntryServiceTest::quickIncome);
        test("quick transfer integration", RecurringQuickEntryServiceTest::quickTransfer);
        test("quick transfer validation", RecurringQuickEntryServiceTest::quickValidation);
        test("test fixtures remain isolated", RecurringQuickEntryServiceTest::isolatedPaths);
        System.out.println(
                "All " + passed + " recurring/quick-entry service tests passed.");
    }

    private static void dueSelection() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.DAILY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            fixture.add(RecurringEntryType.INCOME,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 2, 1), null, true);
            assertEquals(1, fixture.recurring.findDueEntries(
                    LocalDate.of(2024, 1, 15)).size());
        });
    }

    private static void dailyGeneration() throws Exception {
        withFixture(fixture -> {
            RecurringEntry entry = fixture.add(
                    RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.DAILY,
                    1,
                    LocalDate.of(2024, 1, 1),
                    null,
                    true);
            RecurringGenerationResult result = fixture.recurring
                    .generateDueEntries(LocalDate.of(2024, 1, 3));
            assertEquals(3, result.generatedCount());
            assertEquals(3, fixture.expenses.getAllExpenses().size());
            assertEquals(LocalDate.of(2024, 1, 4), fixture.recurring.listAll()
                    .get(0).getNextDueDate());
            assertEquals(entry.getIdentifier(), fixture.recurring.listAll()
                    .get(0).getIdentifier());
        });
    }

    private static void weeklyInterval() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.INCOME,
                    RecurrenceFrequency.WEEKLY, 2,
                    LocalDate.of(2024, 1, 1), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 1, 29));
            assertEquals(3, fixture.income.getAllIncome().size());
            assertEquals(LocalDate.of(2024, 2, 12), fixture.recurring
                    .listAll().get(0).getNextDueDate());
        });
    }

    private static void monthlyGeneration() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 1, 31), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 3, 31));
            assertEquals(
                    List.of(
                        LocalDate.of(2024, 1, 31),
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2024, 3, 31)),
                    fixture.expenses.getAllExpenses().stream()
                            .map(expense -> expense.getDate()).toList());
        });
    }

    private static void yearlyGeneration() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.INCOME,
                    RecurrenceFrequency.YEARLY, 1,
                    LocalDate.of(2024, 2, 29), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2025, 2, 28));
            assertEquals(
                    List.of(LocalDate.of(2024, 2, 29),
                            LocalDate.of(2025, 2, 28)),
                    fixture.income.getAllIncome().stream()
                            .map(entry -> entry.getDate()).toList());
        });
    }

    private static void endDate() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.DAILY, 1,
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 1, 2), true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 1, 5));
            assertEquals(2, fixture.expenses.getAllExpenses().size());
            RecurringEntry ended = fixture.recurring.listAll().get(0);
            assertFalse(ended.isActive());
            assertEquals(LocalDate.of(2024, 1, 3), ended.getNextDueDate());
        });
    }

    private static void inactive() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.DAILY, 1,
                    LocalDate.of(2024, 1, 1), null, false);
            RecurringGenerationResult result = fixture.recurring
                    .generateDueEntries(LocalDate.of(2024, 1, 5));
            assertEquals(0, result.processedCount());
            assertTrue(fixture.expenses.getAllExpenses().isEmpty());
        });
    }

    private static void duplicateRecovery() throws Exception {
        withFixture(fixture -> {
            RecurringEntry entry = fixture.add(
                    RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.DAILY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            String id = "RECURRING_"
                    + entry.getIdentifier().substring("RECURRING_".length())
                    + "_20240101";
            fixture.expenses.createExpenseWithId(
                    id,
                    entry.getDescription(),
                    entry.getAmount(),
                    LocalDate.of(2024, 1, 1),
                    Category.FOOD,
                    Account.DEFAULT,
                    "Generated from recurring entry");
            RecurringGenerationResult result = fixture.recurring
                    .generateDueEntries(LocalDate.of(2024, 1, 1));
            assertEquals(0, result.generatedCount());
            assertEquals(1, result.recoveredOccurrenceCount());
            assertEquals(1, fixture.expenses.getAllExpenses().size());
            assertEquals(LocalDate.of(2024, 1, 2), fixture.recurring
                    .listAll().get(0).getNextDueDate());
        });
    }

    private static void expenseFields() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 1, 1));
            var expense = fixture.expenses.getAllExpenses().get(0);
            assertEquals("Scheduled item", expense.getDescription());
            assertEquals(Category.FOOD, expense.getCategory());
            assertEquals(Account.DEFAULT, expense.getAccount());
        });
    }

    private static void incomeFields() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.INCOME,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 1, 1));
            var income = fixture.income.getAllIncome().get(0);
            assertEquals("Scheduled item", income.getSource());
            assertEquals(Account.DEFAULT, income.getAccount());
        });
    }

    private static void transferFields() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.TRANSFER,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 1, 1));
            var transfer = fixture.transfers.getAllTransfers().get(0);
            assertEquals(Account.DEFAULT, transfer.getSourceAccount());
            assertEquals(fixture.bank, transfer.getDestinationAccount());
        });
    }

    private static void archivedAccount() throws Exception {
        withFixture(fixture -> {
            fixture.recurring.addDefinition(
                    RecurringEntryType.INCOME,
                    new BigDecimal("25.00"),
                    "Archived income",
                    null,
                    fixture.bank,
                    null,
                    RecurrenceFrequency.MONTHLY,
                    1,
                    LocalDate.of(2024, 1, 1),
                    null,
                    true);
            fixture.accounts.archiveAccount(fixture.bank.getIdentifier());
            expect(ValidationException.class, () -> fixture.recurring
                    .generateDueEntries(LocalDate.of(2024, 1, 1)));
            assertTrue(fixture.income.getAllIncome().isEmpty());
        });
    }

    private static void stableUpdate() throws Exception {
        withFixture(fixture -> {
            RecurringEntry original = fixture.add(
                    RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            RecurringEntry updated = fixture.recurring.updateDefinition(
                    original.getIdentifier(),
                    RecurringEntryType.EXPENSE,
                    new BigDecimal("40.00"),
                    "Updated item",
                    Category.BILLS,
                    Account.DEFAULT,
                    null,
                    RecurrenceFrequency.WEEKLY,
                    2,
                    original.getStartDate(),
                    null,
                    original.getNextDueDate(),
                    true);
            assertEquals(original.getIdentifier(), updated.getIdentifier());
            assertEquals(new BigDecimal("40.00"), updated.getAmount());
        });
    }

    private static void activation() throws Exception {
        withFixture(fixture -> {
            RecurringEntry entry = fixture.add(
                    RecurringEntryType.INCOME,
                    RecurrenceFrequency.MONTHLY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            assertFalse(fixture.recurring.setActive(
                    entry.getIdentifier(), false).isActive());
            assertTrue(fixture.recurring.setActive(
                    entry.getIdentifier(), true).isActive());
        });
    }

    private static void quickExpense() throws Exception {
        withFixture(fixture -> {
            QuickEntryResult result = fixture.quick.createEntry(
                    RecurringEntryType.EXPENSE,
                    LocalDate.of(2024, 5, 1),
                    new BigDecimal("12.50"),
                    "Lunch",
                    Category.FOOD,
                    Account.DEFAULT,
                    null);
            assertEquals(RecurringEntryType.EXPENSE, result.type());
            assertEquals(1, fixture.expenses.getAllExpenses().size());
        });
    }

    private static void quickIncome() throws Exception {
        withFixture(fixture -> {
            fixture.quick.createEntry(
                    RecurringEntryType.INCOME,
                    LocalDate.of(2024, 5, 1),
                    new BigDecimal("100.00"),
                    "Salary",
                    null,
                    Account.DEFAULT,
                    null);
            assertEquals("Salary", fixture.income.getAllIncome().get(0).getSource());
        });
    }

    private static void quickTransfer() throws Exception {
        withFixture(fixture -> {
            fixture.quick.createEntry(
                    RecurringEntryType.TRANSFER,
                    LocalDate.of(2024, 5, 1),
                    new BigDecimal("20.00"),
                    "Move funds",
                    null,
                    Account.DEFAULT,
                    fixture.bank);
            assertEquals(1, fixture.transfers.getAllTransfers().size());
        });
    }

    private static void quickValidation() throws Exception {
        withFixture(fixture -> expect(ValidationException.class, () ->
            fixture.quick.createEntry(
                    RecurringEntryType.TRANSFER,
                    LocalDate.of(2024, 5, 1),
                    new BigDecimal("20.00"),
                    "Bad transfer",
                    null,
                    Account.DEFAULT,
                    Account.DEFAULT)));
    }

    private static void isolatedPaths() throws Exception {
        withFixture(fixture -> {
            fixture.add(RecurringEntryType.EXPENSE,
                    RecurrenceFrequency.DAILY, 1,
                    LocalDate.of(2024, 1, 1), null, true);
            fixture.recurring.generateDueEntries(LocalDate.of(2024, 1, 1));
            assertTrue(fixture.directory.getFileName().toString()
                    .startsWith("spendwise-recurring-service-"));
            assertTrue(Files.exists(fixture.directory.resolve("recurring.csv")));
        });
    }

    private static final class Fixture {

        private final Path directory;
        private final CategoryService categories;
        private final AccountService accounts;
        private final Account bank;
        private final ExpenseService expenses;
        private final IncomeService income;
        private final TransferService transfers;
        private final RecurringService recurring;
        private final QuickEntryService quick;

        private Fixture(Path directory) {
            this.directory = directory;
            categories = new CategoryService(new CsvCategoryRepository(
                    directory.resolve("categories.csv")));
            accounts = new AccountService(new CsvAccountRepository(
                    directory.resolve("accounts.csv")));
            bank = accounts.addAccount(
                    "Recurring Bank",
                    AccountType.BANK,
                    new BigDecimal("0.00"));
            expenses = new ExpenseService(
                    new CsvExpenseRepository(
                            directory.resolve("expenses.csv"),
                            categories::resolveCategory,
                            accounts::resolveAccount),
                    accounts);
            income = new IncomeService(
                    new CsvIncomeRepository(
                            directory.resolve("income.csv"),
                            accounts::resolveAccount),
                    accounts);
            transfers = new TransferService(
                    new CsvTransferRepository(
                            directory.resolve("transfers.csv"),
                            accounts::resolveAccount),
                    accounts);
            recurring = new RecurringService(
                    new CsvRecurringEntryRepository(
                            directory.resolve("recurring.csv"),
                            categories::resolveCategory,
                            accounts::resolveAccount),
                    expenses,
                    income,
                    transfers,
                    accounts,
                    categories);
            quick = new QuickEntryService(expenses, income, transfers);
        }

        private RecurringEntry add(
                RecurringEntryType type,
                RecurrenceFrequency frequency,
                int interval,
                LocalDate start,
                LocalDate end,
                boolean active) {
            return recurring.addDefinition(
                    type,
                    new BigDecimal("25.00"),
                    "Scheduled item",
                    type == RecurringEntryType.EXPENSE ? Category.FOOD : null,
                    Account.DEFAULT,
                    type == RecurringEntryType.TRANSFER ? bank : null,
                    frequency,
                    interval,
                    start,
                    end,
                    active);
        }
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path directory = Files.createTempDirectory(
                "spendwise-recurring-service-");
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
