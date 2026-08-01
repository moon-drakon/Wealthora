package com.spendwise.ui;

import com.spendwise.service.AccountService;
import com.spendwise.service.BudgetService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.FinanceService;
import com.spendwise.service.FinancialReportingService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.QuickEntryService;
import com.spendwise.service.RecurringService;
import com.spendwise.service.TransferService;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
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

    public SpendWiseFrame(
            ExpenseService expenseService,
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService,
            CategoryService categoryService,
            AccountService accountService,
            IncomeService incomeService,
            TransferService transferService,
            FinanceService financeService,
            RecurringService recurringService,
            QuickEntryService quickEntryService) {
        super("SpendWise Expense Tracker");
        requireEventDispatchThread();
        Objects.requireNonNull(expenseService, "Expense service is required.");
        Objects.requireNonNull(
                analyticsService, "Expense analytics service is required.");
        Objects.requireNonNull(budgetService, "Budget service is required.");
        Objects.requireNonNull(categoryService, "Category service is required.");
        Objects.requireNonNull(accountService, "Account service is required.");
        Objects.requireNonNull(incomeService, "Income service is required.");
        Objects.requireNonNull(transferService, "Transfer service is required.");
        Objects.requireNonNull(financeService, "Finance service is required.");
        Objects.requireNonNull(recurringService, "Recurring service is required.");
        Objects.requireNonNull(quickEntryService, "Quick-entry service is required.");
        FinancialReportingService reportingService =
                new FinancialReportingService(
                        expenseService,
                        incomeService,
                        transferService,
                        accountService,
                        budgetService);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        DashboardPanel dashboardPanel =
                new DashboardPanel(analyticsService, budgetService);
        BudgetPanel budgetPanel =
                new BudgetPanel(
                        analyticsService, budgetService, categoryService);
        CalendarPanel calendarPanel = new CalendarPanel(reportingService);
        AdvancedReportsPanel reportsPanel = new AdvancedReportsPanel(
                reportingService, accountService, categoryService);
        final ExpensePanel[] expenseReference = new ExpensePanel[1];
        final FinancePanel[] financeReference = new FinancePanel[1];
        final RecurringPanel[] recurringReference = new RecurringPanel[1];
        final QuickEntryDialog[] quickEntryReference = new QuickEntryDialog[1];
        FinancePanel financePanel = new FinancePanel(
                accountService,
                incomeService,
                transferService,
                financeService,
                () -> {
                    if (expenseReference[0] != null) {
                        expenseReference[0].refreshExpenses();
                    }
                    dashboardPanel.refreshDashboard();
                    budgetPanel.refreshBudgetStatus();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                });
        financeReference[0] = financePanel;
        ExpensePanel expensePanel = new ExpensePanel(
                expenseService,
                categoryService,
                accountService,
                category -> expenseService.getAllExpenses().stream()
                        .anyMatch(expense ->
                            expense.getCategory().equals(category))
                        || budgetService.isCategoryReferenced(category),
                () -> {
                    dashboardPanel.refreshDashboard();
                    budgetPanel.refreshBudgetStatus();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                },
                () -> {
                    financePanel.refreshFinanceData();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                });
        expenseReference[0] = expensePanel;
        Runnable refreshFinancialViews = () -> {
            expensePanel.refreshExpenses();
            financePanel.refreshFinanceData();
            dashboardPanel.refreshDashboard();
            budgetPanel.refreshBudgetStatus();
            calendarPanel.refreshCalendar();
            reportsPanel.refreshReports();
            if (recurringReference[0] != null) {
                recurringReference[0].refreshRecurringEntries();
            }
        };
        RecurringPanel recurringPanel = new RecurringPanel(
                recurringService,
                accountService,
                categoryService,
                () -> quickEntryReference[0].open(),
                refreshFinancialViews);
        recurringReference[0] = recurringPanel;
        QuickEntryDialog quickEntryDialog = new QuickEntryDialog(
                this,
                quickEntryService,
                accountService,
                categoryService,
                refreshFinancialViews);
        quickEntryReference[0] = quickEntryDialog;

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Expenses", expensePanel);
        mainTabs.addTab("Dashboard", dashboardPanel);
        mainTabs.addTab("Budgets", budgetPanel);
        mainTabs.addTab("Finance", financePanel);
        mainTabs.addTab("Calendar", calendarPanel);
        mainTabs.addTab("Reports", reportsPanel);
        mainTabs.addTab("Recurring", recurringPanel);
        mainTabs.addChangeListener(event -> {
            if (mainTabs.getSelectedComponent() == dashboardPanel) {
                dashboardPanel.refreshDashboard();
            } else if (mainTabs.getSelectedComponent() == budgetPanel) {
                budgetPanel.refreshBudgetStatus();
            } else if (mainTabs.getSelectedComponent() == financeReference[0]) {
                financeReference[0].refreshFinanceData();
            } else if (mainTabs.getSelectedComponent() == calendarPanel) {
                calendarPanel.refreshCalendar();
            } else if (mainTabs.getSelectedComponent() == reportsPanel) {
                reportsPanel.refreshReports();
            } else if (mainTabs.getSelectedComponent() == recurringPanel) {
                recurringPanel.refreshRecurringEntries();
            }
        });
        JMenuBar menuBar = new JMenuBar();
        JMenu entryMenu = new JMenu("Entry");
        entryMenu.setMnemonic('E');
        JMenuItem quickEntryItem = new JMenuItem("Quick Entry");
        quickEntryItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        quickEntryItem.addActionListener(event -> quickEntryDialog.open());
        entryMenu.add(quickEntryItem);
        menuBar.add(entryMenu);
        setJMenuBar(menuBar);
        setContentPane(mainTabs);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "SpendWiseFrame must be created on the Event Dispatch Thread.");
        }
    }
}
