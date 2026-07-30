package com.spendwise.ui;

import com.spendwise.service.BudgetService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseService;
import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public final class SpendWiseFrame extends JFrame {

    public SpendWiseFrame(
            ExpenseService expenseService,
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService) {
        this(expenseService, analyticsService, budgetService, null);
    }

    public SpendWiseFrame(
            ExpenseService expenseService,
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService,
            CategoryService categoryService) {
        super("SpendWise Expense Tracker");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "SpendWiseFrame must be created on the Event Dispatch Thread.");
        }
        Objects.requireNonNull(expenseService, "Expense service is required.");
        Objects.requireNonNull(
                analyticsService, "Expense analytics service is required.");
        Objects.requireNonNull(budgetService, "Budget service is required.");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        DashboardPanel dashboardPanel =
                new DashboardPanel(analyticsService, budgetService);
        BudgetPanel budgetPanel = categoryService == null
                ? new BudgetPanel(analyticsService, budgetService)
                : new BudgetPanel(analyticsService, budgetService, categoryService);
        ExpensePanel expensePanel = categoryService == null
                ? new ExpensePanel(expenseService)
                : new ExpensePanel(
                        expenseService,
                        categoryService,
                        category -> expenseService.getAllExpenses().stream()
                                .anyMatch(expense ->
                                    expense.getCategory().equals(category))
                                || budgetService.isCategoryReferenced(category),
                        () -> {
                            dashboardPanel.refreshDashboard();
                            budgetPanel.refreshBudgetStatus();
                        });
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Expenses", expensePanel);
        mainTabs.addTab("Dashboard", dashboardPanel);
        mainTabs.addTab("Budgets", budgetPanel);
        mainTabs.addChangeListener(event -> {
            if (mainTabs.getSelectedComponent() == dashboardPanel) {
                dashboardPanel.refreshDashboard();
            } else if (mainTabs.getSelectedComponent() == budgetPanel) {
                budgetPanel.refreshBudgetStatus();
            }
        });
        setContentPane(mainTabs);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }
}
