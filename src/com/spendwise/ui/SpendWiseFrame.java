package com.spendwise.ui;

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
            ExpenseAnalyticsService analyticsService) {
        super("SpendWise Expense Tracker");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "SpendWiseFrame must be created on the Event Dispatch Thread.");
        }
        Objects.requireNonNull(expenseService, "Expense service is required.");
        Objects.requireNonNull(
                analyticsService, "Expense analytics service is required.");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        ExpensePanel expensePanel = new ExpensePanel(expenseService);
        DashboardPanel dashboardPanel = new DashboardPanel(analyticsService);
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Expenses", expensePanel);
        mainTabs.addTab("Dashboard", dashboardPanel);
        mainTabs.addChangeListener(event -> {
            if (mainTabs.getSelectedComponent() == dashboardPanel) {
                dashboardPanel.refreshDashboard();
            }
        });
        setContentPane(mainTabs);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }
}
