package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.service.AccountService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.RecurringService;
import com.spendwise.service.TransferService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class RecurringFoundationTest {

    private static int passed;

    private RecurringFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        test("recurring panel construction is read only",
                RecurringFoundationTest::constructionReadOnly);
        test("recurring panel displays definitions",
                RecurringFoundationTest::displaysDefinitions);
        test("recurring panel generates due entries",
                RecurringFoundationTest::generatesEntries);
        test("recurring panel reports corrupted data",
                RecurringFoundationTest::corruptStatus);
        test("recurring table model is read only",
                RecurringFoundationTest::tableReadOnly);
        System.out.println(
                "All " + passed + " recurring Swing tests passed.");
    }

    private static void constructionReadOnly() throws Exception {
        withFixture(fixture -> {
            RecurringPanel panel = onEdtResult(() -> new RecurringPanel(
                    fixture.recurring,
                    fixture.accounts,
                    fixture.categories,
                    () -> { },
                    () -> { }));
            assertEquals(0, panel.getRowCount());
            assertContains(panel.getStatusText(), "No recurring");
            try (var paths = Files.list(fixture.directory)) {
                assertEquals(0L, paths.count());
            }
        });
    }

    private static void displaysDefinitions() throws Exception {
        withFixture(fixture -> {
            fixture.addDueExpense();
            RecurringPanel panel = onEdtResult(() -> new RecurringPanel(
                    fixture.recurring,
                    fixture.accounts,
                    fixture.categories,
                    () -> { },
                    () -> { }));
            assertEquals(1, panel.getRowCount());
            assertContains(panel.getStatusText(), "1 recurring definition");
        });
    }

    private static void generatesEntries() throws Exception {
        withFixture(fixture -> {
            fixture.addDueExpense();
            AtomicInteger refreshes = new AtomicInteger();
            RecurringPanel panel = onEdtResult(() -> new RecurringPanel(
                    fixture.recurring,
                    fixture.accounts,
                    fixture.categories,
                    () -> { },
                    refreshes::incrementAndGet));
            onEdt(panel::generateDueEntries);
            assertEquals(1, fixture.expenses.getAllExpenses().size());
            assertEquals(1, refreshes.get());
            assertContains(panel.getStatusText(), "Generated 1 entry");
        });
    }

    private static void corruptStatus() throws Exception {
        withFixture(fixture -> {
            Files.writeString(
                    fixture.directory.resolve("recurring.csv"),
                    "wrong,header\n",
                    StandardCharsets.UTF_8);
            RecurringPanel panel = onEdtResult(() -> new RecurringPanel(
                    fixture.recurring,
                    fixture.accounts,
                    fixture.categories,
                    () -> { },
                    () -> { }));
            assertContains(panel.getStatusText(), "Unable to load");
        });
    }

    private static void tableReadOnly() {
        RecurringEntryTableModel model = new RecurringEntryTableModel();
        assertFalse(model.isCellEditable(0, 0));
    }

    private static final class Fixture {

        private final Path directory;
        private final CategoryService categories;
        private final AccountService accounts;
        private final ExpenseService expenses;
        private final RecurringService recurring;

        private Fixture(Path directory) {
            this.directory = directory;
            categories = new CategoryService(new CsvCategoryRepository(
                    directory.resolve("categories.csv")));
            accounts = new AccountService(new CsvAccountRepository(
                    directory.resolve("accounts.csv")));
            expenses = new ExpenseService(
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
        }

        private void addDueExpense() {
            recurring.addDefinition(
                    RecurringEntryType.EXPENSE,
                    new BigDecimal("10.00"),
                    "Today expense",
                    Category.FOOD,
                    Account.DEFAULT,
                    null,
                    RecurrenceFrequency.MONTHLY,
                    1,
                    LocalDate.now(),
                    null,
                    true);
        }
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path directory = Files.createTempDirectory(
                "spendwise-recurring-ui-");
        try {
            action.run(new Fixture(directory));
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void onEdt(ThrowingRunnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError("Swing action failed.", failure.get());
        }
    }

    private static <T> T onEdtResult(ThrowingSupplier<T> supplier)
            throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        onEdt(() -> result.set(supplier.get()));
        return result.get();
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

    private static void assertContains(String actual, String expectedPart) {
        if (actual == null || !actual.contains(expectedPart)) {
            throw new AssertionError(
                    "Expected <" + actual + "> to contain <"
                    + expectedPart + ">.");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false.");
        }
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
    private interface ThrowingSupplier<T> {

        T get() throws Exception;
    }

    @FunctionalInterface
    private interface FixtureAction {

        void run(Fixture fixture) throws Exception;
    }
}
