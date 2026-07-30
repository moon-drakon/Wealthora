package com.spendwise.ui;

import com.spendwise.config.AppPaths;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseService;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class DashboardFoundationTest {

    private static final YearMonth SELECTED_MONTH = YearMonth.of(2024, 6);
    private static int passedTests;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        runSwingTest("empty bar chart construction",
                DashboardFoundationTest::emptyBarChartCanBeCreated);
        runSwingTest("empty donut chart construction",
                DashboardFoundationTest::emptyDonutChartCanBeCreated);
        runSwingTest("null bar data", DashboardFoundationTest::nullBarDataIsRejected);
        runSwingTest("null donut data", DashboardFoundationTest::nullDonutDataIsRejected);
        runSwingTest("null bar key", DashboardFoundationTest::nullBarKeyIsRejected);
        runSwingTest("null bar value", DashboardFoundationTest::nullBarValueIsRejected);
        runSwingTest("null donut key", DashboardFoundationTest::nullDonutKeyIsRejected);
        runSwingTest("null donut value", DashboardFoundationTest::nullDonutValueIsRejected);
        runSwingTest("bar defensive copy", DashboardFoundationTest::barDataIsCopied);
        runSwingTest("donut defensive copy", DashboardFoundationTest::donutDataIsCopied);
        runSwingTest("bar month order", DashboardFoundationTest::barOrderIsPreserved);
        runSwingTest("donut category order", DashboardFoundationTest::donutOrderIsCategoryOrder);
        runSwingTest("bar preferred size", DashboardFoundationTest::barHasSensibleSize);
        runSwingTest("donut preferred size", DashboardFoundationTest::donutHasSensibleSize);
        runSwingTest("empty bar painting", DashboardFoundationTest::emptyBarPaints);
        runSwingTest("populated bar painting", DashboardFoundationTest::populatedBarPaints);
        runSwingTest("empty donut painting", DashboardFoundationTest::emptyDonutPaints);
        runSwingTest("populated donut painting", DashboardFoundationTest::populatedDonutPaints);
        runSwingTest("all-zero painting", DashboardFoundationTest::allZeroDataPaints);
        runSwingTest("exact chart decimals", DashboardFoundationTest::chartDecimalsRemainExact);
        runSwingTest("null analytics service", DashboardFoundationTest::nullAnalyticsIsRejected);
        runSwingTest("headless dashboard construction",
                DashboardFoundationTest::dashboardConstructsHeadlessly);
        runSwingTest("deterministic initial month",
                DashboardFoundationTest::initialMonthIsRespected);
        runSwingTest("empty monthly count", DashboardFoundationTest::emptyCountDisplaysZero);
        runSwingTest("empty monthly total", DashboardFoundationTest::emptyTotalDisplaysZero);
        runSwingTest("empty monthly average", DashboardFoundationTest::emptyAverageDisplaysZero);
        runSwingTest("empty monthly change", DashboardFoundationTest::emptyChangeDisplaysZero);
        runSwingTest("successful dashboard refresh",
                DashboardFoundationTest::refreshDisplaysSelectedExpenses);
        runSwingTest("report repository order",
                DashboardFoundationTest::reportPreservesRepositoryOrder);
        runSwingTest("report cells read-only",
                DashboardFoundationTest::reportCellsAreNotEditable);
        runSwingTest("all category rows", DashboardFoundationTest::breakdownHasEveryCategory);
        runSwingTest("unused category zero", DashboardFoundationTest::unusedCategoryDisplaysZero);
        runSwingTest("six-month chart trend", DashboardFoundationTest::barReceivesSixMonths);
        runSwingTest("selected category chart", DashboardFoundationTest::donutReceivesCategoryTotals);
        runSwingTest("positive change sign", DashboardFoundationTest::positiveChangeHasSign);
        runSwingTest("negative change sign", DashboardFoundationTest::negativeChangeHasSign);
        runSwingTest("refresh mutation safety", DashboardFoundationTest::refreshDoesNotMutateData);
        runSwingTest("refresh current data", DashboardFoundationTest::repeatedRefreshUsesCurrentData);
        runSwingTest("failed refresh table safety",
                DashboardFoundationTest::failedRefreshPreservesTable);
        runSwingTest("failed refresh card safety",
                DashboardFoundationTest::failedRefreshPreservesCards);
        runSwingTest("failed refresh status",
                DashboardFoundationTest::failedRefreshShowsErrorStatus);
        runSwingTest("no production CSV creation",
                DashboardFoundationTest::testCreatesNoProductionCsv);
        runSwingTest("no window or dialog", DashboardFoundationTest::testsRequireNoWindow);

        System.out.println("All " + passedTests + " dashboard foundation tests passed.");
    }

    private static void emptyBarChartCanBeCreated() {
        MonthlyBarChartPanel chart = new MonthlyBarChartPanel();

        assertTrue(chart.getDataSnapshot().isEmpty(),
                "New bar chart should have empty data.");
    }

    private static void emptyDonutChartCanBeCreated() {
        CategoryDonutChartPanel chart = new CategoryDonutChartPanel();

        assertEquals(Category.values().length, chart.getDataSnapshot().size(),
                "New donut chart should include every category.");
    }

    private static void nullBarDataIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new MonthlyBarChartPanel(null),
                "Bar chart should reject null data.");
    }

    private static void nullDonutDataIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new CategoryDonutChartPanel(null),
                "Donut chart should reject null data.");
    }

    private static void nullBarKeyIsRejected() {
        LinkedHashMap<YearMonth, BigDecimal> data = new LinkedHashMap<>();
        data.put(null, new BigDecimal("1.00"));

        expectThrows(
                NullPointerException.class,
                () -> new MonthlyBarChartPanel(data),
                "Bar chart should reject null month keys.");
    }

    private static void nullBarValueIsRejected() {
        LinkedHashMap<YearMonth, BigDecimal> data = new LinkedHashMap<>();
        data.put(SELECTED_MONTH, null);

        expectThrows(
                NullPointerException.class,
                () -> new MonthlyBarChartPanel(data),
                "Bar chart should reject null values.");
    }

    private static void nullDonutKeyIsRejected() {
        Map<Category, BigDecimal> data = new LinkedHashMap<>();
        data.put(null, new BigDecimal("1.00"));

        expectThrows(
                NullPointerException.class,
                () -> new CategoryDonutChartPanel(data),
                "Donut chart should reject null category keys.");
    }

    private static void nullDonutValueIsRejected() {
        Map<Category, BigDecimal> data = new LinkedHashMap<>();
        data.put(Category.FOOD, null);

        expectThrows(
                NullPointerException.class,
                () -> new CategoryDonutChartPanel(data),
                "Donut chart should reject null values.");
    }

    private static void barDataIsCopied() {
        LinkedHashMap<YearMonth, BigDecimal> supplied = monthlyData();
        MonthlyBarChartPanel chart = new MonthlyBarChartPanel(supplied);

        supplied.put(SELECTED_MONTH.plusMonths(1), new BigDecimal("99.00"));

        assertEquals(3, chart.getDataSnapshot().size(),
                "Bar chart retained the caller's mutable map.");
    }

    private static void donutDataIsCopied() {
        Map<Category, BigDecimal> supplied = new LinkedHashMap<>();
        supplied.put(Category.FOOD, new BigDecimal("10.00"));
        CategoryDonutChartPanel chart = new CategoryDonutChartPanel(supplied);

        supplied.put(Category.BILLS, new BigDecimal("99.00"));

        assertMoney("0.00", chart.getDataSnapshot().get(Category.BILLS),
                "Donut chart retained the caller's mutable map.");
    }

    private static void barOrderIsPreserved() {
        MonthlyBarChartPanel chart = new MonthlyBarChartPanel(monthlyData());

        assertEquals(
                List.of(
                        SELECTED_MONTH.minusMonths(2),
                        SELECTED_MONTH.minusMonths(1),
                        SELECTED_MONTH),
                List.copyOf(chart.getDataSnapshot().keySet()),
                "Bar chart did not preserve month order.");
    }

    private static void donutOrderIsCategoryOrder() {
        Map<Category, BigDecimal> supplied = new LinkedHashMap<>();
        supplied.put(Category.OTHER, new BigDecimal("3.00"));
        supplied.put(Category.FOOD, new BigDecimal("2.00"));

        CategoryDonutChartPanel chart = new CategoryDonutChartPanel(supplied);

        assertEquals(
                List.of(Category.values()),
                List.copyOf(chart.getDataSnapshot().keySet()),
                "Donut chart should use Category.values() order.");
    }

    private static void barHasSensibleSize() {
        Dimension size = new MonthlyBarChartPanel().getPreferredSize();

        assertTrue(size.width >= 400 && size.height >= 240,
                "Bar chart preferred size is too small.");
    }

    private static void donutHasSensibleSize() {
        Dimension size = new CategoryDonutChartPanel().getPreferredSize();

        assertTrue(size.width >= 400 && size.height >= 260,
                "Donut chart preferred size is too small.");
    }

    private static void emptyBarPaints() {
        paintPanel(new MonthlyBarChartPanel());
    }

    private static void populatedBarPaints() {
        paintPanel(new MonthlyBarChartPanel(monthlyData()));
    }

    private static void emptyDonutPaints() {
        paintPanel(new CategoryDonutChartPanel());
    }

    private static void populatedDonutPaints() {
        Map<Category, BigDecimal> data = new LinkedHashMap<>();
        data.put(Category.FOOD, new BigDecimal("12.50"));
        data.put(Category.BILLS, new BigDecimal("20.00"));
        paintPanel(new CategoryDonutChartPanel(data));
    }

    private static void allZeroDataPaints() {
        LinkedHashMap<YearMonth, BigDecimal> monthData = new LinkedHashMap<>();
        monthData.put(SELECTED_MONTH.minusMonths(1), new BigDecimal("0.00"));
        monthData.put(SELECTED_MONTH, new BigDecimal("0.00"));
        Map<Category, BigDecimal> categoryData = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            categoryData.put(category, new BigDecimal("0.00"));
        }

        paintPanel(new MonthlyBarChartPanel(monthData));
        paintPanel(new CategoryDonutChartPanel(categoryData));
    }

    private static void chartDecimalsRemainExact() {
        MonthlyBarChartPanel barChart = new MonthlyBarChartPanel();
        LinkedHashMap<YearMonth, BigDecimal> monthData = new LinkedHashMap<>();
        monthData.put(SELECTED_MONTH, new BigDecimal("123456789.12"));
        barChart.replaceData(monthData);

        CategoryDonutChartPanel donutChart = new CategoryDonutChartPanel();
        Map<Category, BigDecimal> categoryData = new LinkedHashMap<>();
        categoryData.put(Category.EDUCATION, new BigDecimal("987654321.09"));
        donutChart.replaceData(categoryData);

        assertEquals(
                new BigDecimal("123456789.12"),
                barChart.getDataSnapshot().get(SELECTED_MONTH),
                "Bar chart changed an exact BigDecimal.");
        assertEquals(
                new BigDecimal("987654321.09"),
                donutChart.getDataSnapshot().get(Category.EDUCATION),
                "Donut chart changed an exact BigDecimal.");
    }

    private static void nullAnalyticsIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new DashboardPanel(null, SELECTED_MONTH),
                "Dashboard should reject a null analytics service.");
    }

    private static void dashboardConstructsHeadlessly() {
        DashboardPanel panel = dashboard(new InMemoryExpenseRepository());

        assertTrue(panel.getComponentCount() > 0,
                "Dashboard should contain non-window Swing components.");
    }

    private static void initialMonthIsRespected() {
        DashboardPanel panel = dashboard(new InMemoryExpenseRepository());

        assertEquals(SELECTED_MONTH, panel.getDisplayedMonth(),
                "Deterministic initial month was not respected.");
    }

    private static void emptyCountDisplaysZero() {
        assertEquals("0", dashboard(new InMemoryExpenseRepository()).getMonthlyCountText(),
                "Empty monthly count should display zero.");
    }

    private static void emptyTotalDisplaysZero() {
        assertEquals("0.00", dashboard(new InMemoryExpenseRepository()).getMonthlyTotalText(),
                "Empty monthly total should display 0.00.");
    }

    private static void emptyAverageDisplaysZero() {
        assertEquals("0.00", dashboard(new InMemoryExpenseRepository()).getMonthlyAverageText(),
                "Empty monthly average should display 0.00.");
    }

    private static void emptyChangeDisplaysZero() {
        assertEquals("0.00", dashboard(new InMemoryExpenseRepository()).getMonthlyChangeText(),
                "Empty monthly change should display 0.00.");
    }

    private static void refreshDisplaysSelectedExpenses() {
        InMemoryExpenseRepository repository = selectedRepository();
        DashboardPanel panel = dashboard(repository);

        assertEquals(3, panel.getReportExpenseRowCount(),
                "Successful refresh did not display selected-month expenses.");
        assertEquals("30.00", panel.getMonthlyTotalText(),
                "Successful refresh displayed the wrong selected-month total.");
    }

    private static void reportPreservesRepositoryOrder() {
        DashboardPanel panel = dashboard(selectedRepository());

        assertEquals("third", panel.getReportExpenseId(0),
                "Report changed the first repository position.");
        assertEquals("first", panel.getReportExpenseId(1),
                "Report changed the second repository position.");
        assertEquals("second", panel.getReportExpenseId(2),
                "Report changed the third repository position.");
    }

    private static void reportCellsAreNotEditable() {
        DashboardPanel panel = dashboard(selectedRepository());

        for (int column = 0; column < 5; column++) {
            assertFalse(panel.isReportExpenseCellEditable(0, column),
                    "Monthly report expense cells must not be editable.");
        }
    }

    private static void breakdownHasEveryCategory() {
        DashboardPanel panel = dashboard(selectedRepository());

        assertEquals(Category.values().length, panel.getCategoryRowCount(),
                "Category breakdown should include every category.");
        for (int row = 0; row < Category.values().length; row++) {
            assertEquals(Category.values()[row], panel.getCategoryAt(row),
                    "Category breakdown order is incorrect.");
        }
    }

    private static void unusedCategoryDisplaysZero() {
        DashboardPanel panel = dashboard(selectedRepository());
        int transportRow = Category.TRANSPORT.ordinal();

        assertMoney("0.00", panel.getCategoryTotalAt(transportRow),
                "Unused category should display 0.00.");
    }

    private static void barReceivesSixMonths() {
        DashboardPanel panel = dashboard(selectedRepository());
        List<YearMonth> months = List.copyOf(panel.getBarChartData().keySet());

        assertEquals(6, months.size(), "Bar chart should receive six months.");
        assertEquals(SELECTED_MONTH.minusMonths(5), months.get(0),
                "Bar chart starts at the wrong month.");
        assertEquals(SELECTED_MONTH, months.get(5),
                "Bar chart ends at the wrong month.");
    }

    private static void donutReceivesCategoryTotals() {
        DashboardPanel panel = dashboard(selectedRepository());

        assertMoney("20.00", panel.getDonutChartData().get(Category.FOOD),
                "Donut chart received the wrong food total.");
        assertMoney("10.00", panel.getDonutChartData().get(Category.BILLS),
                "Donut chart received the wrong bills total.");
    }

    private static void positiveChangeHasSign() {
        InMemoryExpenseRepository repository = changeRepository("20.00", "30.00");

        assertEquals("+10.00", dashboard(repository).getMonthlyChangeText(),
                "Positive change should display a leading plus sign.");
    }

    private static void negativeChangeHasSign() {
        InMemoryExpenseRepository repository = changeRepository("30.00", "20.00");

        assertEquals("-10.00", dashboard(repository).getMonthlyChangeText(),
                "Negative change should display its sign.");
    }

    private static void refreshDoesNotMutateData() {
        InMemoryExpenseRepository repository = selectedRepository();
        int sizeBefore = repository.size();
        int mutationsBefore = repository.getMutationCalls();
        DashboardPanel panel = dashboard(repository);

        panel.refreshDashboard();

        assertEquals(sizeBefore, repository.size(), "Refresh changed repository size.");
        assertEquals(mutationsBefore, repository.getMutationCalls(),
                "Refresh invoked a repository mutation.");
    }

    private static void repeatedRefreshUsesCurrentData() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        DashboardPanel panel = dashboard(repository);
        repository.seed(expense(
                "new", SELECTED_MONTH.atDay(5), "8.00", Category.OTHER));

        panel.refreshDashboard();

        assertEquals(1, panel.getReportExpenseRowCount(),
                "Repeated refresh did not use current data.");
        assertEquals("8.00", panel.getMonthlyTotalText(),
                "Repeated refresh retained stale analytics.");
    }

    private static void failedRefreshPreservesTable() {
        InMemoryExpenseRepository repository = selectedRepository();
        DashboardPanel panel = dashboard(repository);
        List<String> before = reportIds(panel);
        repository.failReads(new RepositoryException("Test dashboard read failure."));

        panel.refreshDashboard();

        assertEquals(before, reportIds(panel),
                "Failed refresh replaced previously displayed report rows.");
    }

    private static void failedRefreshPreservesCards() {
        InMemoryExpenseRepository repository = selectedRepository();
        DashboardPanel panel = dashboard(repository);
        String countBefore = panel.getMonthlyCountText();
        String totalBefore = panel.getMonthlyTotalText();
        String averageBefore = panel.getMonthlyAverageText();
        String changeBefore = panel.getMonthlyChangeText();
        repository.failReads(new RepositoryException("Test dashboard read failure."));

        panel.refreshDashboard();

        assertEquals(countBefore, panel.getMonthlyCountText(),
                "Failed refresh changed the count card.");
        assertEquals(totalBefore, panel.getMonthlyTotalText(),
                "Failed refresh changed the total card.");
        assertEquals(averageBefore, panel.getMonthlyAverageText(),
                "Failed refresh changed the average card.");
        assertEquals(changeBefore, panel.getMonthlyChangeText(),
                "Failed refresh changed the change card.");
    }

    private static void failedRefreshShowsErrorStatus() {
        InMemoryExpenseRepository repository = selectedRepository();
        DashboardPanel panel = dashboard(repository);
        repository.failReads(new RepositoryException("Test dashboard read failure."));

        panel.refreshDashboard();

        assertTrue(panel.getStatusText().toLowerCase().contains("failed"),
                "Failed refresh should report an error status.");
        assertTrue(panel.getStatusText().contains("Test dashboard read failure."),
                "Error status should contain the safe repository message.");
    }

    private static void testCreatesNoProductionCsv() {
        Path productionCsv = AppPaths.getExpenseCsvPath();
        boolean existedBefore = Files.exists(productionCsv);

        dashboard(selectedRepository()).refreshDashboard();

        assertEquals(existedBefore, Files.exists(productionCsv),
                "Dashboard foundation test changed production CSV existence.");
    }

    private static void testsRequireNoWindow() {
        assertTrue(GraphicsEnvironment.isHeadless(),
                "Dashboard foundation suite should run headlessly.");
        dashboard(new InMemoryExpenseRepository());
        assertEquals(0, Window.getWindows().length,
                "Dashboard foundation tests should not create a window or dialog.");
    }

    private static DashboardPanel dashboard(InMemoryExpenseRepository repository) {
        ExpenseService expenseService = new ExpenseService(repository);
        return new DashboardPanel(
                new ExpenseAnalyticsService(expenseService),
                SELECTED_MONTH);
    }

    private static InMemoryExpenseRepository selectedRepository() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "third", SELECTED_MONTH.atDay(20), "10.00", Category.FOOD));
        repository.seed(expense(
                "first", SELECTED_MONTH.atDay(1), "10.00", Category.BILLS));
        repository.seed(expense(
                "second", SELECTED_MONTH.atDay(10), "10.00", Category.FOOD));
        repository.seed(expense(
                "outside",
                SELECTED_MONTH.minusMonths(2).atDay(10),
                "99.00",
                Category.OTHER));
        return repository;
    }

    private static InMemoryExpenseRepository changeRepository(
            String previousAmount, String selectedAmount) {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "previous",
                SELECTED_MONTH.minusMonths(1).atDay(10),
                previousAmount,
                Category.OTHER));
        repository.seed(expense(
                "selected",
                SELECTED_MONTH.atDay(10),
                selectedAmount,
                Category.OTHER));
        return repository;
    }

    private static Expense expense(
            String id,
            LocalDate date,
            String amount,
            Category category) {
        return new Expense(
                id,
                "Expense " + id,
                new BigDecimal(amount),
                date,
                category,
                "Notes " + id);
    }

    private static LinkedHashMap<YearMonth, BigDecimal> monthlyData() {
        LinkedHashMap<YearMonth, BigDecimal> data = new LinkedHashMap<>();
        data.put(SELECTED_MONTH.minusMonths(2), new BigDecimal("10.00"));
        data.put(SELECTED_MONTH.minusMonths(1), new BigDecimal("20.00"));
        data.put(SELECTED_MONTH, new BigDecimal("30.00"));
        return data;
    }

    private static List<String> reportIds(DashboardPanel panel) {
        List<String> ids = new ArrayList<>();
        for (int row = 0; row < panel.getReportExpenseRowCount(); row++) {
            ids.add(panel.getReportExpenseId(row));
        }
        return List.copyOf(ids);
    }

    private static void paintPanel(JPanel panel) {
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        panel.doLayout();
        BufferedImage image = new BufferedImage(
                size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(new BigDecimal(expected), actual, message);
        assertEquals(2, actual.scale(), message + " Scale should be two.");
    }

    private static void runSwingTest(String name, TestCase test) {
        runTest(name, () -> runOnEventDispatchThread(test));
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError("Dashboard foundation test failed: " + name, exception);
        }
    }

    private static void runOnEventDispatchThread(TestCase test)
            throws InvocationTargetException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                test.run();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return;
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, TestCase action, String message) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    message + " Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(message + " Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }

    private static final class InMemoryExpenseRepository implements ExpenseRepository {

        private final List<Expense> expenses = new ArrayList<>();
        private int mutationCalls;
        private RepositoryException readFailure;

        @Override
        public List<Expense> findAll() {
            if (readFailure != null) {
                throw readFailure;
            }
            return List.copyOf(expenses);
        }

        @Override
        public Optional<Expense> findById(String id) {
            return expenses.stream()
                    .filter(expense -> expense.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Expense expense) {
            mutationCalls++;
            if (findById(expense.getId()).isPresent()) {
                throw new RepositoryException(
                        "Expense ID already exists: " + expense.getId());
            }
            expenses.add(Objects.requireNonNull(expense, "Expense is required."));
        }

        @Override
        public void update(Expense expense) {
            mutationCalls++;
            Objects.requireNonNull(expense, "Expense is required.");
            for (int index = 0; index < expenses.size(); index++) {
                if (expenses.get(index).getId().equals(expense.getId())) {
                    expenses.set(index, expense);
                    return;
                }
            }
            throw new RepositoryException(
                    "Expense ID does not exist: " + expense.getId());
        }

        @Override
        public boolean deleteById(String id) {
            mutationCalls++;
            return expenses.removeIf(expense -> expense.getId().equals(id));
        }

        void seed(Expense expense) {
            expenses.add(expense);
        }

        int size() {
            return expenses.size();
        }

        int getMutationCalls() {
            return mutationCalls;
        }

        void failReads(RepositoryException failure) {
            readFailure = failure;
        }
    }
}
