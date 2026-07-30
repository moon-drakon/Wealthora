package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.BudgetService;
import com.spendwise.service.BudgetStatusSnapshot;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseAnalyticsSnapshot;
import com.spendwise.service.ExpenseService;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class CategoryManagementTest {

    private static int passedTests;

    private CategoryManagementTest() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        run("table columns", CategoryManagementTest::tableColumnsAreCorrect);
        run("table values", CategoryManagementTest::tableShowsTypeAndStatus);
        run("table read only", CategoryManagementTest::tableIsReadOnly);
        run("built-in actions", CategoryManagementTest::builtInActionsAreDisabled);
        run("active custom actions", CategoryManagementTest::activeCustomActionsAreCorrect);
        run("archived custom actions", CategoryManagementTest::archivedCustomActionsAreCorrect);
        run("filter refresh", CategoryManagementTest::expenseFilterRefreshes);
        run("active add selector", CategoryManagementTest::activeCategoryIsSelectable);
        run("archived add selector", CategoryManagementTest::archivedCategoryIsNotSelectable);
        run("archived edit selector", CategoryManagementTest::archivedExistingValueRemainsSelectable);
        run("historical summary", CategoryManagementTest::archivedExpenseRemainsInSummary);
        run("historical report", CategoryManagementTest::archivedExpenseRemainsInDashboard);
        run("archived budget", CategoryManagementTest::archivedBudgetRemainsVisibleReadOnly);
        run("drafts survive category add",
                CategoryManagementTest::draftsSurviveCategoryAdd);
        run("drafts survive category restore",
                CategoryManagementTest::draftsSurviveCategoryRestore);
        run("drafts survive category archive",
                CategoryManagementTest::draftsSurviveCategoryArchive);
        run("failed refresh preserves drafts",
                CategoryManagementTest::failedRefreshPreservesDrafts);
        run("category refresh failure preserves drafts and view",
                CategoryManagementTest::categoryRefreshFailurePreservesDraftsAndView);
        run("wrapped refresh failure preserves success",
                CategoryManagementTest::wrappedRefreshFailurePreservesSuccess);
        run("runtime refresh failure is contained",
                CategoryManagementTest::runtimeRefreshFailureIsContained);
        run("refresh warning feedback",
                CategoryManagementTest::refreshWarningFeedbackReportsSaved);
        run("mutation failure remains failure",
                CategoryManagementTest::mutationFailureRemainsFailure);
        run("renamed expense display", CategoryManagementTest::renamedCategoryResolvesForExpense);
        run("renamed budget display", CategoryManagementTest::renamedCategoryResolvesForBudget);
        run("construction side effects", CategoryManagementTest::panelConstructionCreatesNoCsv);
        run("refresh side effects", CategoryManagementTest::panelRefreshCreatesNoCsv);
        run("path siblings", CategoryManagementTest::categoryPathIsProductionSibling);
        System.out.println(
                "All " + passedTests + " category management tests passed.");
    }

    private static void tableColumnsAreCorrect() {
        CategoryTableModel model = new CategoryTableModel();
        assertEquals(List.of("Name", "Type", "Status"),
                java.util.stream.IntStream.range(0, model.getColumnCount())
                        .mapToObj(model::getColumnName)
                        .toList(),
                "Category table columns are incorrect.");
    }

    private static void tableShowsTypeAndStatus() {
        CategoryTableModel model = new CategoryTableModel();
        model.replaceCategories(List.of(
                Category.FOOD, custom("CUSTOM_001", "Travel", true)));
        assertEquals("Food", model.getValueAt(0, 0), "Built-in name is incorrect.");
        assertEquals("Built-in", model.getValueAt(0, 1), "Built-in type is incorrect.");
        assertEquals("Active", model.getValueAt(0, 2), "Built-in status is incorrect.");
        assertEquals("Custom", model.getValueAt(1, 1), "Custom type is incorrect.");
        assertEquals("Archived", model.getValueAt(1, 2), "Archived status is incorrect.");
    }

    private static void tableIsReadOnly() {
        CategoryTableModel model = new CategoryTableModel();
        model.replaceCategories(List.of(Category.FOOD));
        for (int column = 0; column < model.getColumnCount(); column++) {
            assertFalse(model.isCellEditable(0, column),
                    "Category table exposed an editable cell.");
        }
    }

    private static void builtInActionsAreDisabled() {
        CategoryManagerDialog.ActionState state =
                CategoryManagerDialog.actionStateFor(Category.OTHER);
        assertFalse(state.renameEnabled(), "Built-in rename was enabled.");
        assertFalse(state.archiveEnabled(), "Built-in archive was enabled.");
        assertFalse(state.restoreEnabled(), "Built-in restore was enabled.");
    }

    private static void activeCustomActionsAreCorrect() {
        CategoryManagerDialog.ActionState state =
                CategoryManagerDialog.actionStateFor(
                        custom("CUSTOM_001", "Travel", false));
        assertTrue(state.renameEnabled(), "Custom rename was disabled.");
        assertTrue(state.archiveEnabled(), "Active custom archive was disabled.");
        assertFalse(state.restoreEnabled(), "Active custom restore was enabled.");
    }

    private static void archivedCustomActionsAreCorrect() {
        CategoryManagerDialog.ActionState state =
                CategoryManagerDialog.actionStateFor(
                        custom("CUSTOM_001", "Travel", true));
        assertTrue(state.renameEnabled(), "Archived custom rename was disabled.");
        assertFalse(state.archiveEnabled(), "Archived custom archive was enabled.");
        assertTrue(state.restoreEnabled(), "Archived custom restore was disabled.");
    }

    private static void expenseFilterRefreshes() throws Exception {
        InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
        CategoryService categoryService = new CategoryService(categories);
        AtomicReference<ExpensePanel> panelReference = new AtomicReference<>();
        onEdt(() -> panelReference.set(new ExpensePanel(
                new ExpenseService(new InMemoryExpenseRepository()),
                categoryService,
                category -> false,
                () -> {
                })));
        ExpensePanel panel = panelReference.get();
        int initialCount = panel.getCategoryFilterChoiceCount();
        categoryService.addCategory("Travel");
        onEdt(panel::refreshCategoryChoices);
        assertEquals(initialCount + 1, panel.getCategoryFilterChoiceCount(),
                "Expense category filter did not refresh.");
        assertEquals("Travel",
                panel.getCategoryFilterChoiceAt(initialCount).getDisplayName(),
                "Refreshed filter did not include the custom category.");
    }

    private static void activeCategoryIsSelectable() throws Exception {
        Category active = custom("CUSTOM_001", "Travel", false);
        ExpensePanel panel = expensePanel(repositoryWith(active));
        assertTrue(panel.getSelectableCategorySnapshot(null).contains(active),
                "Active custom category was not selectable for new expenses.");
    }

    private static void archivedCategoryIsNotSelectable() throws Exception {
        Category archived = custom("CUSTOM_001", "Travel", true);
        ExpensePanel panel = expensePanel(repositoryWith(archived));
        assertFalse(panel.getSelectableCategorySnapshot(null).contains(archived),
                "Archived category was selectable for a new expense.");
    }

    private static void archivedExistingValueRemainsSelectable() throws Exception {
        Category archived = custom("CUSTOM_001", "Travel", true);
        Expense existing = expense("historical", archived);
        ExpensePanel panel = expensePanel(repositoryWith(archived));
        assertTrue(panel.getSelectableCategorySnapshot(existing).contains(archived),
                "Editing lost the archived historical category.");
    }

    private static void archivedExpenseRemainsInSummary() {
        Category archived = custom("CUSTOM_001", "Travel", true);
        InMemoryExpenseRepository expenses = new InMemoryExpenseRepository();
        expenses.add(expense("historical", archived));
        var summary = new ExpenseService(expenses).calculateOverallSummary();
        assertEquals(new BigDecimal("12.50"), summary.getTotalForCategory(archived),
                "Archived expense was lost from category totals.");
    }

    private static void archivedExpenseRemainsInDashboard() throws Exception {
        Category archived = custom("CUSTOM_001", "Travel", true);
        InMemoryExpenseRepository expenses = new InMemoryExpenseRepository();
        expenses.add(expense("historical", archived));
        ExpenseService expenseService = new ExpenseService(expenses);
        AtomicReference<DashboardPanel> panelReference = new AtomicReference<>();
        onEdt(() -> panelReference.set(new DashboardPanel(
                new ExpenseAnalyticsService(expenseService),
                new BudgetService(new InMemoryBudgetRepository()),
                YearMonth.of(2025, 1))));
        DashboardPanel panel = panelReference.get();
        boolean found = false;
        for (int row = 0; row < panel.getCategoryRowCount(); row++) {
            if (panel.getCategoryAt(row).equals(archived)) {
                found = true;
                assertEquals(new BigDecimal("12.50"), panel.getCategoryTotalAt(row),
                        "Archived dashboard total is incorrect.");
            }
        }
        assertTrue(found, "Dashboard omitted archived historical spending.");
    }

    private static void archivedBudgetRemainsVisibleReadOnly() {
        Category archived = custom("CUSTOM_001", "Travel", true);
        YearMonth month = YearMonth.of(2025, 1);
        MonthlyBudget budget = new MonthlyBudget(
                month, Optional.empty(), Map.of(archived, new BigDecimal("50.00")));
        InMemoryBudgetRepository budgets = new InMemoryBudgetRepository();
        budgets.saved.put(month, budget);
        ExpenseService expenses = new ExpenseService(new InMemoryExpenseRepository());
        ExpenseAnalyticsSnapshot analytics =
                new ExpenseAnalyticsService(expenses).analyzeMonth(month);
        BudgetStatusSnapshot status = new BudgetService(budgets).evaluate(analytics);
        BudgetLimitTableModel model = new BudgetLimitTableModel();
        model.replaceStatus(status, List.of(Category.values()));
        int row = rowOf(model, archived);
        assertTrue(row >= 0, "Archived configured budget disappeared.");
        assertEquals(new BigDecimal("50.00"), model.getLimitValueAt(row),
                "Archived budget limit changed.");
        assertFalse(model.isCellEditable(row, 2),
                "Archived category accepted a new budget edit.");
    }

    private static void draftsSurviveCategoryAdd() throws Exception {
        InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
        CategoryService categories = new CategoryService(repository);
        BudgetPanel panel = budgetPanel(categories);
        YearMonth selectedPeriod = YearMonth.of(2026, 4);
        onEdt(() -> {
            panel.setOverallLimitText("700.00");
            panel.setSelectedPeriod(selectedPeriod);
            int foodRow = rowOf(panel.getBudgetTableModel(), Category.FOOD);
            assertTrue(panel.getCategoryTable().editCellAt(foodRow, 2),
                    "The active budget editor did not start.");
            if (!(panel.getCategoryTable().getEditorComponent()
                    instanceof JTextField editor)) {
                throw new AssertionError("Budget limit editor is not a text field.");
            }
            editor.setText("125.00");
        });

        categories.addCategory("Travel");
        onEdt(panel::refreshBudgetStatus);

        assertEquals("700.00", panel.getOverallLimitEditorText(),
                "Adding a category cleared the overall-limit draft.");
        assertEquals("125.00", draftFor(panel, Category.FOOD),
                "Adding a category detached the Food draft.");
        assertEquals(selectedPeriod, panel.getSelectedPeriod(),
                "Adding a category changed the selected month or year.");
        assertTrue(panel.hasUnsavedChanges(),
                "Adding a category cleared the unsaved-change state.");
    }

    private static void draftsSurviveCategoryRestore() throws Exception {
        Category travel = custom("CUSTOM_001", "Travel", true);
        InMemoryCategoryRepository repository = repositoryWith(travel);
        CategoryService categories = new CategoryService(repository);
        BudgetPanel panel = budgetPanel(categories);
        onEdt(() -> panel.getBudgetTableModel().setValueAt(
                "80.00", rowOf(panel.getBudgetTableModel(), Category.BILLS), 2));

        categories.restoreCategory(travel.getIdentifier());
        onEdt(panel::refreshBudgetStatus);

        assertEquals("80.00", draftFor(panel, Category.BILLS),
                "Restoring a category detached an existing draft.");
        assertEquals("", draftFor(panel, travel),
                "A restored category did not start with its loaded blank value.");
    }

    private static void draftsSurviveCategoryArchive() throws Exception {
        Category travel = custom("CUSTOM_001", "Travel", false);
        Category gifts = custom("CUSTOM_002", "Gifts", false);
        InMemoryCategoryRepository repository = repositoryWith(travel, gifts);
        CategoryService categories = new CategoryService(repository);
        BudgetPanel panel = budgetPanel(categories);
        onEdt(() -> {
            panel.getBudgetTableModel().setValueAt(
                    "45.00", rowOf(panel.getBudgetTableModel(), Category.HEALTH), 2);
            panel.getBudgetTableModel().setValueAt(
                    "60.00", rowOf(panel.getBudgetTableModel(), gifts), 2);
        });

        categories.archiveCategory(travel.getIdentifier());
        onEdt(panel::refreshBudgetStatus);

        assertEquals("45.00", draftFor(panel, Category.HEALTH),
                "Archiving shifted the Health draft.");
        assertEquals("60.00", draftFor(panel, gifts),
                "Archiving shifted another custom-category draft.");
        assertEquals(-1, rowOf(panel.getBudgetTableModel(), travel),
                "Unreferenced archived category remained a new-budget row.");
    }

    private static void failedRefreshPreservesDrafts() throws Exception {
        InMemoryCategoryRepository categoryRepository =
                new InMemoryCategoryRepository();
        CategoryService categories = new CategoryService(categoryRepository);
        InMemoryBudgetRepository budgets = new InMemoryBudgetRepository();
        BudgetPanel panel = budgetPanel(categories, budgets);
        YearMonth selectedPeriod = YearMonth.of(2027, 8);
        onEdt(() -> {
            panel.setOverallLimitText("900.00");
            panel.getBudgetTableModel().setValueAt(
                    "75.00", rowOf(panel.getBudgetTableModel(), Category.EDUCATION), 2);
            panel.setSelectedPeriod(selectedPeriod);
        });
        budgets.failure = new RepositoryException("Test refresh failure.");

        onEdt(panel::refreshBudgetStatus);

        assertEquals("900.00", panel.getOverallLimitEditorText(),
                "Failed refresh cleared the overall-limit draft.");
        assertEquals("75.00", draftFor(panel, Category.EDUCATION),
                "Failed refresh cleared a category-limit draft.");
        assertEquals(selectedPeriod, panel.getSelectedPeriod(),
                "Failed refresh changed the selected month or year.");
        assertTrue(panel.hasUnsavedChanges(),
                "Failed refresh cleared the unsaved-change state.");
    }

    private static void categoryRefreshFailurePreservesDraftsAndView()
            throws Exception {
        InMemoryCategoryRepository categoryRepository =
                new InMemoryCategoryRepository();
        CategoryService categories = new CategoryService(categoryRepository);
        BudgetPanel panel = budgetPanel(categories);
        YearMonth displayedPeriod = panel.getDisplayedMonth();
        YearMonth selectedPeriod = YearMonth.of(2027, 8);
        int originalRowCount = panel.getBudgetTableModel().getRowCount();
        onEdt(() -> {
            panel.setOverallLimitText("900.00");
            panel.getBudgetTableModel().setValueAt(
                    "75.00",
                    rowOf(panel.getBudgetTableModel(), Category.EDUCATION),
                    2);
            panel.setSelectedPeriod(selectedPeriod);
        });
        categoryRepository.failure =
                new RepositoryException("Test category refresh failure.");

        onEdt(panel::refreshBudgetStatus);

        assertEquals("900.00", panel.getOverallLimitEditorText(),
                "Category refresh failure cleared the overall-limit draft.");
        assertEquals("75.00", draftFor(panel, Category.EDUCATION),
                "Category refresh failure cleared a category-limit draft.");
        assertEquals(selectedPeriod, panel.getSelectedPeriod(),
                "Category refresh failure changed the selected period.");
        assertEquals(displayedPeriod, panel.getDisplayedMonth(),
                "Category refresh failure changed the displayed period.");
        assertEquals(originalRowCount, panel.getBudgetTableModel().getRowCount(),
                "Category refresh failure partially replaced the budget rows.");
        assertTrue(panel.hasUnsavedChanges(),
                "Category refresh failure cleared the unsaved-change state.");
    }

    private static void wrappedRefreshFailurePreservesSuccess() {
        Category saved = custom("CUSTOM_001", "Travel", false);
        RepositoryException wrappedFailure = new RepositoryException(
                "Wrapped view refresh failure.", new IOException("Test I/O failure."));
        AtomicInteger managerRefreshes = new AtomicInteger();

        CategoryManagerDialog.MutationResult result =
                CategoryManagerDialog.executeMutation(
                        () -> saved,
                        category -> managerRefreshes.incrementAndGet(),
                        () -> {
                            throw wrappedFailure;
                        });

        assertTrue(result.mutationSucceeded(),
                "A persisted mutation was changed into a failure.");
        assertTrue(result.savedCategory() == saved,
                "The saved category was not retained.");
        assertEquals(1, managerRefreshes.get(),
                "The category manager was not refreshed after persistence.");
        assertEquals(List.of(wrappedFailure), result.refreshFailures(),
                "The wrapped refresh failure was not retained.");
        assertTrue(result.refreshFailures().get(0).getCause() instanceof IOException,
                "The checked refresh cause was not preserved.");
    }

    private static void runtimeRefreshFailureIsContained() {
        Category saved = custom("CUSTOM_001", "Travel", false);
        RuntimeException refreshFailure =
                new IllegalStateException("Test listener failure.");
        CategoryManagerDialog.MutationResult result =
                CategoryManagerDialog.executeMutation(
                        () -> saved,
                        category -> {
                        },
                        () -> {
                            throw refreshFailure;
                        });
        assertTrue(result.mutationSucceeded(),
                "Listener RuntimeException escaped the success boundary.");
        assertEquals(List.of(refreshFailure), result.refreshFailures(),
                "Listener RuntimeException was not retained.");
    }

    private static void refreshWarningFeedbackReportsSaved() {
        Category saved = custom("CUSTOM_001", "Travel", false);
        CategoryManagerDialog.MutationResult result =
                CategoryManagerDialog.executeMutation(
                        () -> saved,
                        category -> {
                        },
                        () -> {
                            throw new RepositoryException("Test refresh failure.");
                        });
        String message = CategoryManagerDialog.feedbackMessage(
                "Category added.", result);
        assertTrue(message.startsWith("Category added."),
                "Refresh warning did not report the saved mutation.");
        assertTrue(message.contains("could not refresh"),
                "Refresh warning did not explain the view failure.");
        assertFalse(message.contains("Operation Failed"),
                "Refresh warning incorrectly reported mutation failure.");
    }

    private static void mutationFailureRemainsFailure() {
        ValidationException mutationFailure =
                new ValidationException("Duplicate category name.");
        AtomicInteger refreshCalls = new AtomicInteger();
        CategoryManagerDialog.MutationResult result =
                CategoryManagerDialog.executeMutation(
                        () -> {
                            throw mutationFailure;
                        },
                        category -> refreshCalls.incrementAndGet(),
                        refreshCalls::incrementAndGet);

        assertFalse(result.mutationSucceeded(),
                "An actual mutation failure was reported as success.");
        assertTrue(result.mutationFailure() == mutationFailure,
                "The mutation failure was not retained.");
        assertEquals(0, refreshCalls.get(),
                "Refresh ran after a failed mutation.");
        assertEquals(
                "Duplicate category name.",
                CategoryManagerDialog.feedbackMessage("Category added.", result),
                "Mutation failure feedback claimed success.");
    }

    private static void renamedCategoryResolvesForExpense() throws Exception {
        withDirectory(directory -> {
            CsvCategoryRepository categoryRepository =
                    new CsvCategoryRepository(directory.resolve("categories.csv"));
            CategoryService categories = new CategoryService(categoryRepository);
            Category travel = categories.addCategory("Travel");
            CsvExpenseRepository expenses = new CsvExpenseRepository(
                    directory.resolve("expenses.csv"), categories::resolveCategory);
            expenses.add(expense("saved", travel));
            categories.renameCategory(travel.getIdentifier(), "Trips");
            assertEquals("Trips",
                    expenses.findAll().get(0).getCategory().getDisplayName(),
                    "Historical expense did not resolve the renamed display name.");
        });
    }

    private static void renamedCategoryResolvesForBudget() throws Exception {
        withDirectory(directory -> {
            CsvCategoryRepository categoryRepository =
                    new CsvCategoryRepository(directory.resolve("categories.csv"));
            CategoryService categories = new CategoryService(categoryRepository);
            Category travel = categories.addCategory("Travel");
            CsvBudgetRepository budgets = new CsvBudgetRepository(
                    directory.resolve("budgets.csv"), categories::resolveCategory);
            YearMonth month = YearMonth.of(2025, 1);
            budgets.save(new MonthlyBudget(
                    month, Optional.empty(), Map.of(travel, new BigDecimal("50.00"))));
            categories.renameCategory(travel.getIdentifier(), "Trips");
            Category resolved = budgets.findByMonth(month).orElseThrow()
                    .getCategoryLimits().keySet().iterator().next();
            assertEquals("Trips", resolved.getDisplayName(),
                    "Historical budget did not resolve the renamed display name.");
        });
    }

    private static void panelConstructionCreatesNoCsv() throws Exception {
        withDirectory(directory -> {
            Path categoriesPath = directory.resolve("categories.csv");
            Path expensesPath = directory.resolve("expenses.csv");
            Path budgetsPath = directory.resolve("budgets.csv");
            CategoryService categories =
                    new CategoryService(new CsvCategoryRepository(categoriesPath));
            CsvExpenseRepository expenseRepository = new CsvExpenseRepository(
                    expensesPath, categories::resolveCategory);
            ExpenseService expenses = new ExpenseService(expenseRepository);
            ExpenseAnalyticsService analytics = new ExpenseAnalyticsService(expenses);
            BudgetService budgets = new BudgetService(new CsvBudgetRepository(
                    budgetsPath, categories::resolveCategory));
            onEdt(() -> {
                new ExpensePanel(expenses, categories, category -> false, () -> {
                });
                new DashboardPanel(analytics, budgets);
                new BudgetPanel(analytics, budgets, categories);
            });
            assertNoCsv(categoriesPath, expensesPath, budgetsPath);
        });
    }

    private static void panelRefreshCreatesNoCsv() throws Exception {
        withDirectory(directory -> {
            Path categoriesPath = directory.resolve("categories.csv");
            Path expensesPath = directory.resolve("expenses.csv");
            Path budgetsPath = directory.resolve("budgets.csv");
            CategoryService categories =
                    new CategoryService(new CsvCategoryRepository(categoriesPath));
            ExpenseService expenses = new ExpenseService(new CsvExpenseRepository(
                    expensesPath, categories::resolveCategory));
            ExpenseAnalyticsService analytics = new ExpenseAnalyticsService(expenses);
            BudgetService budgets = new BudgetService(new CsvBudgetRepository(
                    budgetsPath, categories::resolveCategory));
            AtomicReference<ExpensePanel> expensePanel = new AtomicReference<>();
            AtomicReference<DashboardPanel> dashboardPanel = new AtomicReference<>();
            AtomicReference<BudgetPanel> budgetPanel = new AtomicReference<>();
            onEdt(() -> {
                expensePanel.set(new ExpensePanel(
                        expenses, categories, category -> false, () -> {
                        }));
                dashboardPanel.set(new DashboardPanel(analytics, budgets));
                budgetPanel.set(new BudgetPanel(analytics, budgets, categories));
                expensePanel.get().refreshCategoryChoices();
                dashboardPanel.get().refreshDashboard();
                budgetPanel.get().refreshBudgetStatus();
            });
            assertNoCsv(categoriesPath, expensesPath, budgetsPath);
        });
    }

    private static void categoryPathIsProductionSibling() {
        Path categories = com.spendwise.config.AppPaths.getCategoryCsvPath();
        Path expenses = com.spendwise.config.AppPaths.getExpenseCsvPath();
        Path budgets = com.spendwise.config.AppPaths.getBudgetCsvPath();
        assertEquals(expenses.getParent(), categories.getParent(),
                "Category data is not beside expense data.");
        assertEquals(budgets.getParent(), categories.getParent(),
                "Category data is not beside budget data.");
        assertEquals("categories.csv", categories.getFileName().toString(),
                "Category data filename is incorrect.");
    }

    private static ExpensePanel expensePanel(
            InMemoryCategoryRepository categoryRepository) throws Exception {
        AtomicReference<ExpensePanel> panelReference = new AtomicReference<>();
        CategoryService categoryService = new CategoryService(categoryRepository);
        onEdt(() -> panelReference.set(new ExpensePanel(
                new ExpenseService(new InMemoryExpenseRepository()),
                categoryService,
                category -> false,
                () -> {
                })));
        return panelReference.get();
    }

    private static BudgetPanel budgetPanel(CategoryService categoryService)
            throws Exception {
        return budgetPanel(categoryService, new InMemoryBudgetRepository());
    }

    private static BudgetPanel budgetPanel(
            CategoryService categoryService, InMemoryBudgetRepository budgetRepository)
            throws Exception {
        AtomicReference<BudgetPanel> panelReference = new AtomicReference<>();
        ExpenseService expenses =
                new ExpenseService(new InMemoryExpenseRepository());
        ExpenseAnalyticsService analytics = new ExpenseAnalyticsService(expenses);
        BudgetService budgets = new BudgetService(budgetRepository);
        onEdt(() -> panelReference.set(
                new BudgetPanel(analytics, budgets, categoryService, YearMonth.of(2025, 1))));
        return panelReference.get();
    }

    private static String draftFor(BudgetPanel panel, Category category) {
        int row = rowOf(panel.getBudgetTableModel(), category);
        if (row < 0) {
            throw new AssertionError("Missing budget row for " + category.getDisplayName());
        }
        return panel.getBudgetTableModel().getLimitTextAt(row);
    }

    private static int rowOf(BudgetLimitTableModel model, Category category) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (model.getCategoryAt(row).equals(category)) {
                return row;
            }
        }
        return -1;
    }

    private static Expense expense(String id, Category category) {
        return new Expense(
                id,
                "Historical expense",
                new BigDecimal("12.50"),
                LocalDate.of(2025, 1, 15),
                category,
                "");
    }

    private static Category custom(String id, String name, boolean archived) {
        return Category.createCustom(id, name, archived);
    }

    private static InMemoryCategoryRepository repositoryWith(Category... categories) {
        InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
        repository.categories.addAll(List.of(categories));
        return repository;
    }

    private static void assertNoCsv(Path... paths) {
        for (Path path : paths) {
            assertFalse(Files.exists(path), "Read-only UI flow created " + path);
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
            throw new RuntimeException(failure.get());
        }
    }

    private static void withDirectory(ThrowingConsumer<Path> action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-category-ui-test-");
        try {
            action.accept(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
        }
    }

    private static void run(String name, ThrowingRunnable test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError(
                    "Category management test failed: " + name, exception);
        }
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
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual);
        }
    }

    private static final class InMemoryCategoryRepository
            implements CategoryRepository {

        private final List<Category> categories = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public List<Category> findAll() {
            if (failure != null) {
                throw failure;
            }
            return List.copyOf(categories);
        }

        @Override
        public void add(Category category) {
            categories.add(category);
        }

        @Override
        public void update(Category category) {
            for (int index = 0; index < categories.size(); index++) {
                if (categories.get(index).equals(category)) {
                    categories.set(index, category);
                    return;
                }
            }
            throw new RepositoryException("Missing category.");
        }
    }

    private static final class InMemoryExpenseRepository
            implements ExpenseRepository {

        private final List<Expense> expenses = new ArrayList<>();

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
            expenses.add(expense);
        }

        @Override
        public void update(Expense expense) {
            for (int index = 0; index < expenses.size(); index++) {
                if (expenses.get(index).equals(expense)) {
                    expenses.set(index, expense);
                    return;
                }
            }
            throw new RepositoryException("Missing expense.");
        }

        @Override
        public boolean deleteById(String id) {
            return expenses.removeIf(expense -> expense.getId().equals(id));
        }
    }

    private static final class InMemoryBudgetRepository
            implements BudgetRepository {

        private final Map<YearMonth, MonthlyBudget> saved = new LinkedHashMap<>();
        private RuntimeException failure;

        @Override
        public Optional<MonthlyBudget> findByMonth(YearMonth month) {
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(saved.get(month));
        }

        @Override
        public void save(MonthlyBudget budget) {
            saved.put(budget.getMonth(), budget);
        }

        @Override
        public boolean delete(YearMonth month) {
            return saved.remove(month) != null;
        }

        @Override
        public boolean isCategoryReferenced(Category category) {
            return saved.values().stream().anyMatch(
                    budget -> budget.getCategoryLimits().containsKey(category));
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
}
