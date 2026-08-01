package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.service.AccountService;
import com.spendwise.service.AdvancedReportSnapshot;
import com.spendwise.service.BudgetService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.FinancialReportingService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.TransferService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class CalendarReportsFoundationTest {

    private static int passed;

    private CalendarReportsFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        test("calendar panel construction and alignment",
                CalendarReportsFoundationTest::calendarConstruction);
        test("calendar day selection details",
                CalendarReportsFoundationTest::daySelection);
        test("calendar navigation and empty state",
                CalendarReportsFoundationTest::navigationAndEmptyState);
        test("advanced report panel construction",
                CalendarReportsFoundationTest::reportConstruction);
        test("advanced report invalid range",
                CalendarReportsFoundationTest::invalidRange);
        test("calendar and reports remain read only",
                CalendarReportsFoundationTest::panelsReadOnly);
        test("activity table is read only",
                CalendarReportsFoundationTest::activityTableReadOnly);
        System.out.println(
                "All " + passed + " calendar/report Swing tests passed.");
    }

    private static void calendarConstruction() throws Exception {
        withFixture(fixture -> {
            CalendarPanel panel = onEdtResult(() ->
                new CalendarPanel(
                        fixture.reporting, YearMonth.of(2024, 1)));
            assertEquals(YearMonth.of(2024, 1), panel.getDisplayedMonth());
            assertEquals(1, panel.getFirstDayColumn());
        });
    }

    private static void daySelection() throws Exception {
        withFixture(fixture -> {
            CalendarPanel panel = onEdtResult(() ->
                new CalendarPanel(
                        fixture.reporting, YearMonth.of(2024, 1)));
            onEdt(() -> panel.selectDate(LocalDate.of(2024, 1, 2)));
            assertEquals(LocalDate.of(2024, 1, 2), panel.getSelectedDate());
            assertEquals(3, panel.getDetailRowCount());
            assertContains(panel.getStatusText(), "3 entries");
        });
    }

    private static void navigationAndEmptyState() throws Exception {
        withFixture(fixture -> {
            CalendarPanel panel = onEdtResult(() ->
                new CalendarPanel(
                        fixture.reporting, YearMonth.of(2024, 2)));
            onEdt(panel::showNextMonth);
            assertEquals(YearMonth.of(2024, 3), panel.getDisplayedMonth());
            assertContains(panel.getStatusText(), "No financial activity");
            onEdt(panel::showPreviousMonth);
            assertEquals(YearMonth.of(2024, 2), panel.getDisplayedMonth());
        });
    }

    private static void reportConstruction() throws Exception {
        withFixture(fixture -> {
            AdvancedReportsPanel panel = onEdtResult(() ->
                new AdvancedReportsPanel(
                        fixture.reporting,
                        fixture.accounts,
                        fixture.categories));
            onEdt(() -> {
                panel.setDateRange(
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 2, 29));
                panel.refreshReports();
            });
            AdvancedReportSnapshot report = panel.getLatestSnapshot();
            assertMoney("150.00", report.getTotalIncome());
            assertMoney("50.00", report.getTotalExpenses());
            assertEquals(2, panel.getCategoryRowCount());
            assertEquals(2, panel.getAccountRowCount());
        });
    }

    private static void invalidRange() throws Exception {
        withFixture(fixture -> {
            AdvancedReportsPanel panel = onEdtResult(() ->
                new AdvancedReportsPanel(
                        fixture.reporting,
                        fixture.accounts,
                        fixture.categories));
            onEdt(() -> {
                panel.setDateRange(
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 1, 1));
                panel.refreshReports();
            });
            assertContains(panel.getStatusText(), "start date");
        });
    }

    private static void panelsReadOnly() throws Exception {
        withFixture(fixture -> {
            Map<String, byte[]> before = fixture.fileBytes();
            onEdt(() -> {
                CalendarPanel calendar = new CalendarPanel(
                        fixture.reporting, YearMonth.of(2024, 1));
                AdvancedReportsPanel reports = new AdvancedReportsPanel(
                        fixture.reporting,
                        fixture.accounts,
                        fixture.categories);
                calendar.refreshCalendar();
                reports.setDateRange(
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 2, 29));
                reports.refreshReports();
            });
            assertFileBytes(before, fixture.fileBytes());
        });
    }

    private static void activityTableReadOnly() {
        FinancialActivityTableModel model =
                new FinancialActivityTableModel();
        assertFalse(model.isCellEditable(0, 0));
    }

    private static void withFixture(ThrowingConsumer<Fixture> action)
            throws Exception {
        Path directory = Files.createTempDirectory(
                "spendwise-calendar-report-test-");
        try {
            action.accept(new Fixture(directory));
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
        private final CategoryService categories;
        private final AccountService accounts;
        private final FinancialReportingService reporting;

        private Fixture(Path directory) {
            this.directory = directory;
            categories = new CategoryService(new CsvCategoryRepository(
                    directory.resolve("categories.csv")));
            accounts = new AccountService(new CsvAccountRepository(
                    directory.resolve("accounts.csv")));
            Account bank = accounts.addAccount(
                    "Savings", AccountType.BANK, new BigDecimal("10.00"));
            ExpenseService expenses = new ExpenseService(
                    new CsvExpenseRepository(
                            directory.resolve("expenses.csv"),
                            categories::resolveCategory,
                            accounts::resolveAccount));
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
            expenses.createExpense(
                    "Lunch",
                    new BigDecimal("30.00"),
                    LocalDate.of(2024, 1, 2),
                    Category.FOOD,
                    Account.DEFAULT,
                    "");
            expenses.createExpense(
                    "Internet",
                    new BigDecimal("20.00"),
                    LocalDate.of(2024, 2, 10),
                    Category.BILLS,
                    bank,
                    "");
            income.createIncome(
                    LocalDate.of(2024, 1, 2),
                    new BigDecimal("100.00"),
                    "Salary",
                    Account.DEFAULT,
                    "");
            income.createIncome(
                    LocalDate.of(2024, 2, 10),
                    new BigDecimal("50.00"),
                    "Bonus",
                    bank,
                    "");
            transfers.createTransfer(
                    LocalDate.of(2024, 1, 2),
                    new BigDecimal("40.00"),
                    Account.DEFAULT,
                    bank,
                    "Move savings");
            reporting = new FinancialReportingService(
                    expenses, income, transfers, accounts, budgets);
        }

        private Map<String, byte[]> fileBytes() throws IOException {
            LinkedHashMap<String, byte[]> bytes = new LinkedHashMap<>();
            try (var paths = Files.list(directory)) {
                for (Path path : paths.sorted().toList()) {
                    if (Files.isRegularFile(path)) {
                        bytes.put(path.getFileName().toString(),
                                Files.readAllBytes(path));
                    }
                }
            }
            return bytes;
        }
    }

    private static void assertFileBytes(
            Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String name : expected.keySet()) {
            if (!java.util.Arrays.equals(expected.get(name), actual.get(name))) {
                throw new AssertionError("File changed during report: " + name);
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

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean value) {
        assertTrue(!value);
    }

    private static void assertContains(String actual, String part) {
        if (actual == null || !actual.contains(part)) {
            throw new AssertionError(
                    "Expected <" + actual + "> to contain <" + part + ">.");
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
        assertEquals(2, actual.scale());
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
    private interface ThrowingConsumer<T> {

        void accept(T value) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {

        T get() throws Exception;
    }
}
