package com.spendwise.ui;

import com.spendwise.config.AppPaths;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.BudgetAlertLevel;
import com.spendwise.service.BudgetService;
import com.spendwise.service.BudgetStatusSnapshot;
import com.spendwise.service.BudgetUsage;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseService;
import com.spendwise.validation.ValidationException;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class BudgetFoundationTest {

    private static final YearMonth MONTH = YearMonth.of(2024, 6);
    private static int passedTests;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        runSwingTest("table model construction", BudgetFoundationTest::tableModelConstructs);
        runSwingTest("category row count", BudgetFoundationTest::tableHasEveryCategory);
        runSwingTest("category row order", BudgetFoundationTest::tableUsesCategoryOrder);
        runSwingTest("limit-only editing", BudgetFoundationTest::onlyLimitIsEditable);
        runSwingTest("blank unset limit", BudgetFoundationTest::unsetLimitIsBlank);
        runSwingTest("exact spent value", BudgetFoundationTest::spentValueIsExact);
        runSwingTest("exact limit value", BudgetFoundationTest::limitValueIsExact);
        runSwingTest("remaining display", BudgetFoundationTest::remainingIsCorrect);
        runSwingTest("status display", BudgetFoundationTest::statusIsCorrect);
        runSwingTest("model event", BudgetFoundationTest::replacementFiresEvent);
        runSwingTest("null analytics", BudgetFoundationTest::nullAnalyticsIsRejected);
        runSwingTest("null budget service", BudgetFoundationTest::nullBudgetIsRejected);
        runTest("EDT enforcement", BudgetFoundationTest::panelRequiresEdt);
        runSwingTest("initial month", BudgetFoundationTest::initialMonthIsRespected);
        runSwingTest("initial year", BudgetFoundationTest::initialYearIsRespected);
        runSwingTest("empty limit display", BudgetFoundationTest::emptyLimitDisplaysNotSet);
        runSwingTest("empty spending display", BudgetFoundationTest::emptySpendingDisplaysZero);
        runSwingTest("spending refresh", BudgetFoundationTest::refreshShowsSpending);
        runSwingTest("configured limit display", BudgetFoundationTest::refreshShowsLimit);
        runSwingTest("remaining card", BudgetFoundationTest::refreshShowsRemaining);
        runSwingTest("percentage card", BudgetFoundationTest::refreshShowsPercentage);
        runSwingTest("warning card", BudgetFoundationTest::refreshShowsWarning);
        runSwingTest("panel category rows", BudgetFoundationTest::panelHasEveryCategory);
        runSwingTest("panel unconfigured category", BudgetFoundationTest::unconfiguredCategoryIsBlank);
        runSwingTest("current expense refresh", BudgetFoundationTest::refreshUsesCurrentExpenses);
        runSwingTest("current budget refresh", BudgetFoundationTest::refreshUsesCurrentBudget);
        runSwingTest("expense mutation safety", BudgetFoundationTest::refreshDoesNotMutateExpenses);
        runSwingTest("budget write safety", BudgetFoundationTest::refreshDoesNotWriteBudget);
        runSwingTest("failed refresh cards", BudgetFoundationTest::failurePreservesCards);
        runSwingTest("failed refresh table", BudgetFoundationTest::failurePreservesTable);
        runSwingTest("failed refresh status", BudgetFoundationTest::failureReportsStatus);
        runTest("valid limit parsing", BudgetFoundationTest::validInputParses);
        runTest("blank limit parsing", BudgetFoundationTest::blankInputIsEmpty);
        runSwingTest("invalid editor preservation", BudgetFoundationTest::invalidInputRemainsVisible);
        runSwingTest("no frame", BudgetFoundationTest::noFrameIsRequired);
        runSwingTest("no dialog", BudgetFoundationTest::noDialogIsRequired);
        runSwingTest("no production CSV", BudgetFoundationTest::noProductionCsvIsCreated);
        runSwingTest("dashboard construction", BudgetFoundationTest::dashboardStillConstructs);
        runSwingTest("dashboard budget data", BudgetFoundationTest::dashboardReceivesBudgetStatus);
        runSwingTest("chart painting", BudgetFoundationTest::chartsRemainHeadlessSafe);

        System.out.println("All " + passedTests + " budget foundation tests passed.");
    }

    private static void tableModelConstructs() {
        assertEquals(5, new BudgetLimitTableModel().getColumnCount(),
                "Budget table should have five columns.");
    }

    private static void tableHasEveryCategory() {
        assertEquals(Category.values().length,
                new BudgetLimitTableModel().getRowCount(),
                "Budget table row count is incorrect.");
    }

    private static void tableUsesCategoryOrder() {
        BudgetLimitTableModel model = new BudgetLimitTableModel();
        for (int row = 0; row < model.getRowCount(); row++) {
            assertEquals(Category.values()[row], model.getCategoryAt(row),
                    "Budget table category order is incorrect.");
        }
    }

    private static void onlyLimitIsEditable() {
        BudgetLimitTableModel model = new BudgetLimitTableModel();
        for (int column = 0; column < model.getColumnCount(); column++) {
            assertEquals(column == 2, model.isCellEditable(0, column),
                    "Only the Limit column should be editable.");
        }
    }

    private static void unsetLimitIsBlank() {
        BudgetLimitTableModel model = new BudgetLimitTableModel();
        model.replaceStatus(status(
                "12.00", MonthlyBudget.empty(MONTH), Map.of()));
        assertEquals("", model.getValueAt(Category.FOOD.ordinal(), 2),
                "Unset limit should display as blank.");
    }

    private static void spentValueIsExact() {
        BudgetLimitTableModel model = populatedModel();
        Object spent = model.getValueAt(Category.FOOD.ordinal(), 1);
        assertEquals(BigDecimal.class, spent.getClass(),
                "Spent value should remain BigDecimal.");
        assertMoney("12.34", (BigDecimal) spent, "Spent value changed.");
    }

    private static void limitValueIsExact() {
        Object limit = populatedModel().getValueAt(Category.FOOD.ordinal(), 2);
        assertEquals(BigDecimal.class, limit.getClass(),
                "Configured limit should remain BigDecimal.");
        assertMoney("20.00", (BigDecimal) limit, "Limit value changed.");
    }

    private static void remainingIsCorrect() {
        Object remaining =
                populatedModel().getValueAt(Category.FOOD.ordinal(), 3);
        assertMoney("7.66", (BigDecimal) remaining,
                "Remaining table value is incorrect.");
    }

    private static void statusIsCorrect() {
        assertEquals(
                BudgetAlertLevel.WITHIN_LIMIT,
                populatedModel().getValueAt(Category.FOOD.ordinal(), 4),
                "Table warning status is incorrect.");
    }

    private static void replacementFiresEvent() {
        BudgetLimitTableModel model = new BudgetLimitTableModel();
        AtomicInteger events = new AtomicInteger();
        model.addTableModelListener(event -> events.incrementAndGet());
        model.replaceStatus(status("0.00", MonthlyBudget.empty(MONTH), Map.of()));
        assertEquals(1, events.get(), "Replacement should fire one table event.");
    }

    private static void nullAnalyticsIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new BudgetPanel(
                        null,
                        new BudgetService(new InMemoryBudgetRepository()),
                        MONTH));
    }

    private static void nullBudgetIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new BudgetPanel(
                        analytics(new InMemoryExpenseRepository()),
                        null,
                        MONTH));
        expectThrows(
                NullPointerException.class,
                () -> new DashboardPanel(
                        analytics(new InMemoryExpenseRepository()),
                        null,
                        MONTH));
    }

    private static void panelRequiresEdt() {
        expectThrows(
                IllegalStateException.class,
                () -> new BudgetPanel(
                        analytics(new InMemoryExpenseRepository()),
                        new BudgetService(new InMemoryBudgetRepository()),
                        MONTH));
    }

    private static void initialMonthIsRespected() {
        assertEquals(MONTH, fixture().panel.getDisplayedMonth(),
                "Initial month was not respected.");
    }

    private static void initialYearIsRespected() {
        assertEquals(MONTH.getYear(), fixture().panel.getSelectedYear(),
                "Initial year was not respected.");
    }

    private static void emptyLimitDisplaysNotSet() {
        assertEquals("Not set", fixture().panel.getLimitText(),
                "Empty limit should display Not set.");
    }

    private static void emptySpendingDisplaysZero() {
        assertEquals("0.00", fixture().panel.getSpentText(),
                "Empty spending should display 0.00.");
    }

    private static void refreshShowsSpending() {
        Fixture fixture = populatedFixture("20.00");
        assertEquals("12.34", fixture.panel.getSpentText(),
                "Panel displayed the wrong spending.");
    }

    private static void refreshShowsLimit() {
        assertEquals("20.00", populatedFixture("20.00").panel.getLimitText(),
                "Panel displayed the wrong limit.");
    }

    private static void refreshShowsRemaining() {
        assertEquals("7.66", populatedFixture("20.00").panel.getRemainingText(),
                "Panel displayed the wrong remaining amount.");
    }

    private static void refreshShowsPercentage() {
        assertEquals("61.70%", populatedFixture("20.00").panel.getPercentageText(),
                "Panel displayed the wrong usage percentage.");
    }

    private static void refreshShowsWarning() {
        assertEquals("Near limit", populatedFixture("15.00").panel.getWarningText(),
                "Panel displayed the wrong warning.");
    }

    private static void panelHasEveryCategory() {
        assertEquals(Category.values().length,
                fixture().panel.getBudgetTableModel().getRowCount(),
                "Panel table is missing categories.");
    }

    private static void unconfiguredCategoryIsBlank() {
        Fixture fixture = populatedFixture("20.00");
        assertEquals("",
                fixture.panel.getBudgetTableModel()
                        .getValueAt(Category.BILLS.ordinal(), 2),
                "Unconfigured category should remain blank.");
    }

    private static void refreshUsesCurrentExpenses() {
        Fixture fixture = fixture();
        fixture.expenses.seed(expense("new", "8.00"));
        fixture.panel.refreshBudgetStatus();
        assertEquals("8.00", fixture.panel.getSpentText(),
                "Repeated refresh did not use current expense data.");
    }

    private static void refreshUsesCurrentBudget() {
        Fixture fixture = populatedFixture("20.00");
        fixture.budgets.seed(overall("10.00"));
        fixture.panel.refreshBudgetStatus();
        assertEquals("10.00", fixture.panel.getLimitText(),
                "Repeated refresh did not use current budget data.");
    }

    private static void refreshDoesNotMutateExpenses() {
        Expense expense = expense("one", "12.34");
        Fixture fixture = fixture();
        fixture.expenses.seed(expense);
        int mutations = fixture.expenses.mutations;
        fixture.panel.refreshBudgetStatus();
        assertEquals(mutations, fixture.expenses.mutations,
                "Budget refresh invoked an expense mutation.");
        assertEquals("Expense one", expense.getDescription(),
                "Budget refresh mutated an expense.");
    }

    private static void refreshDoesNotWriteBudget() {
        Fixture fixture = populatedFixture("20.00");
        int writes = fixture.budgets.saves + fixture.budgets.deletes;
        fixture.panel.refreshBudgetStatus();
        assertEquals(writes, fixture.budgets.saves + fixture.budgets.deletes,
                "Budget refresh performed a budget write.");
    }

    private static void failurePreservesCards() {
        Fixture fixture = populatedFixture("20.00");
        String before = fixture.panel.getLimitText()
                + fixture.panel.getSpentText()
                + fixture.panel.getRemainingText()
                + fixture.panel.getPercentageText()
                + fixture.panel.getWarningText();
        fixture.budgets.failure = new RepositoryException("Test budget failure.");
        fixture.panel.refreshBudgetStatus();
        String after = fixture.panel.getLimitText()
                + fixture.panel.getSpentText()
                + fixture.panel.getRemainingText()
                + fixture.panel.getPercentageText()
                + fixture.panel.getWarningText();
        assertEquals(before, after, "Failed refresh changed successful cards.");
    }

    private static void failurePreservesTable() {
        Fixture fixture = populatedFixture("20.00");
        Object before = fixture.panel.getBudgetTableModel()
                .getValueAt(Category.FOOD.ordinal(), 2);
        fixture.budgets.failure = new RepositoryException("Test budget failure.");
        fixture.panel.refreshBudgetStatus();
        assertEquals(before,
                fixture.panel.getBudgetTableModel()
                        .getValueAt(Category.FOOD.ordinal(), 2),
                "Failed refresh changed successful table data.");
    }

    private static void failureReportsStatus() {
        Fixture fixture = populatedFixture("20.00");
        fixture.budgets.failure = new RepositoryException("Test budget failure.");
        fixture.panel.refreshBudgetStatus();
        assertTrue(fixture.panel.getStatusText().toLowerCase().contains("failed"),
                "Failed refresh did not report failure.");
        assertTrue(fixture.panel.getStatusText().contains("Test budget failure."),
                "Failed refresh did not retain the safe message.");
    }

    private static void validInputParses() {
        assertMoney(
                "123.45",
                BudgetPanel.parseOptionalLimit(" 123.45 ", "Limit").orElseThrow(),
                "Valid limit did not parse exactly.");
    }

    private static void blankInputIsEmpty() {
        assertTrue(BudgetPanel.parseOptionalLimit("   ", "Limit").isEmpty(),
                "Blank input should mean unconfigured.");
    }

    private static void invalidInputRemainsVisible() {
        Fixture fixture = fixture();
        fixture.panel.setOverallLimitText("invalid amount");
        expectThrows(
                ValidationException.class,
                () -> BudgetPanel.parseOptionalLimit(
                        fixture.panel.getOverallLimitEditorText(), "Overall limit"));
        fixture.panel.refreshBudgetStatus();
        assertEquals("invalid amount", fixture.panel.getOverallLimitEditorText(),
                "Invalid input was reset.");
        assertTrue(fixture.panel.hasUnsavedChanges(),
                "Invalid editor change was not marked unsaved.");
    }

    private static void noFrameIsRequired() {
        fixture();
        assertEquals(0, Window.getWindows().length,
                "Budget foundation test created a window.");
    }

    private static void noDialogIsRequired() {
        Fixture fixture = fixture();
        fixture.panel.refreshBudgetStatus();
        assertEquals(0, Window.getWindows().length,
                "Budget refresh created a dialog.");
    }

    private static void noProductionCsvIsCreated() {
        Path expensePath = AppPaths.getExpenseCsvPath();
        Path budgetPath = AppPaths.getBudgetCsvPath();
        boolean expenseBefore = Files.exists(expensePath);
        boolean budgetBefore = Files.exists(budgetPath);
        fixture().panel.refreshBudgetStatus();
        assertEquals(expenseBefore, Files.exists(expensePath),
                "Test changed production expense CSV existence.");
        assertEquals(budgetBefore, Files.exists(budgetPath),
                "Test changed production budget CSV existence.");
    }

    private static void dashboardStillConstructs() {
        Fixture fixture = populatedFixture("20.00");
        DashboardPanel dashboard = new DashboardPanel(
                analytics(fixture.expenses),
                new BudgetService(fixture.budgets),
                MONTH);
        assertTrue(dashboard.getComponentCount() > 0,
                "Dashboard no longer constructs headlessly.");
    }

    private static void dashboardReceivesBudgetStatus() {
        Fixture fixture = populatedFixture("20.00");
        DashboardPanel dashboard = new DashboardPanel(
                analytics(fixture.expenses),
                new BudgetService(fixture.budgets),
                MONTH);
        assertEquals("20.00", dashboard.getBudgetLimitText(),
                "Dashboard did not receive the budget limit.");
        assertEquals("12.34", dashboard.getBudgetSpentText(),
                "Dashboard did not receive selected-month spending.");
    }

    private static void chartsRemainHeadlessSafe() {
        paint(new MonthlyBarChartPanel());
        paint(new CategoryDonutChartPanel());
    }

    private static BudgetLimitTableModel populatedModel() {
        BudgetLimitTableModel model = new BudgetLimitTableModel();
        model.replaceStatus(status(
                "12.34",
                new MonthlyBudget(
                        MONTH,
                        Optional.empty(),
                        Map.of(Category.FOOD, new BigDecimal("20.00"))),
                Map.of(Category.FOOD, "12.34")));
        return model;
    }

    private static Fixture populatedFixture(String overallLimit) {
        Fixture fixture = fixture();
        fixture.expenses.seed(expense("one", "12.34"));
        fixture.budgets.seed(overall(overallLimit));
        fixture.panel.refreshBudgetStatus();
        return fixture;
    }

    private static Fixture fixture() {
        InMemoryExpenseRepository expenses = new InMemoryExpenseRepository();
        InMemoryBudgetRepository budgets = new InMemoryBudgetRepository();
        BudgetPanel panel = new BudgetPanel(
                analytics(expenses),
                new BudgetService(budgets),
                MONTH);
        return new Fixture(expenses, budgets, panel);
    }

    private static ExpenseAnalyticsService analytics(
            InMemoryExpenseRepository repository) {
        return new ExpenseAnalyticsService(new ExpenseService(repository));
    }

    private static BudgetStatusSnapshot status(
            String total,
            MonthlyBudget budget,
            Map<Category, String> categoryTotals) {
        BudgetUsage overall = new BudgetUsage(
                new BigDecimal(total), budget.getOverallLimit());
        EnumMap<Category, BudgetUsage> usages = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            usages.put(
                    category,
                    new BudgetUsage(
                            new BigDecimal(
                                    categoryTotals.getOrDefault(category, "0.00")),
                            budget.getCategoryLimit(category)));
        }
        return new BudgetStatusSnapshot(MONTH, overall, usages);
    }

    private static MonthlyBudget overall(String amount) {
        return new MonthlyBudget(
                MONTH, Optional.of(new BigDecimal(amount)), Map.of());
    }

    private static Expense expense(String id, String amount) {
        return new Expense(
                id,
                "Expense " + id,
                new BigDecimal(amount),
                MONTH.atDay(10),
                Category.FOOD,
                "Notes " + id);
    }

    private static void paint(JPanel panel) {
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

    private static void runSwingTest(String name, TestCase test) {
        runTest(name, () -> runOnEventDispatchThread(test));
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError(
                    "Budget foundation test failed: " + name, exception);
        }
    }

    private static void runOnEventDispatchThread(TestCase test)
            throws InvocationTargetException, InterruptedException {
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
            Class<T> expectedType, TestCase action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError("Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertMoney(
            String expected, BigDecimal actual, String message) {
        assertEquals(new BigDecimal(expected), actual, message);
        assertEquals(2, actual.scale(), message + " Scale should be two.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }

    private record Fixture(
            InMemoryExpenseRepository expenses,
            InMemoryBudgetRepository budgets,
            BudgetPanel panel) {
    }

    private static final class InMemoryBudgetRepository
            implements BudgetRepository {

        private MonthlyBudget budget;
        private int saves;
        private int deletes;
        private RepositoryException failure;

        @Override
        public Optional<MonthlyBudget> findByMonth(YearMonth month) {
            if (failure != null) {
                throw failure;
            }
            return budget != null && budget.getMonth().equals(month)
                    ? Optional.of(budget)
                    : Optional.empty();
        }

        @Override
        public void save(MonthlyBudget budget) {
            saves++;
            this.budget = budget;
        }

        @Override
        public boolean delete(YearMonth month) {
            deletes++;
            if (budget == null || !budget.getMonth().equals(month)) {
                return false;
            }
            budget = null;
            return true;
        }

        void seed(MonthlyBudget budget) {
            this.budget = budget;
        }
    }

    private static final class InMemoryExpenseRepository
            implements ExpenseRepository {

        private final List<Expense> expenses = new ArrayList<>();
        private int mutations;

        @Override
        public List<Expense> findAll() {
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
            mutations++;
            expenses.add(expense);
        }

        @Override
        public void update(Expense expense) {
            mutations++;
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            mutations++;
            return false;
        }

        void seed(Expense expense) {
            expenses.add(expense);
        }
    }
}
