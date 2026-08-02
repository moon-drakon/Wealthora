package com.spendwise.ui;

import com.spendwise.service.AccountService;
import com.spendwise.service.AccountStatementService;
import com.spendwise.service.BackupService;
import com.spendwise.service.BudgetService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.ExportService;
import com.spendwise.service.FinanceService;
import com.spendwise.service.FinancialReportingService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.QuickEntryService;
import com.spendwise.service.RecurringService;
import com.spendwise.service.TransferService;
import com.spendwise.service.CurrencyService;
import com.spendwise.service.PaymentCardService;
import com.spendwise.service.AdvancedBudgetService;
import com.spendwise.service.SavingsGoalService;
import com.spendwise.service.DebtService;
import com.spendwise.service.PortfolioAnalyticsService;
import com.spendwise.service.FinanceNotificationService;
import com.spendwise.service.JsonBackupService;
import com.spendwise.service.CsvImportService;
import com.spendwise.service.PdfReportService;
import com.spendwise.ui.component.AppIcons;
import com.spendwise.ui.shell.AppShellPanel;
import com.spendwise.ui.theme.AppTheme;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JComponent;
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
            QuickEntryService quickEntryService,
            BackupService backupService,
            ExportService exportService) {
        this(expenseService, analyticsService, budgetService, categoryService,
                accountService, incomeService, transferService, financeService,
                recurringService, quickEntryService, backupService, exportService,
                null, null);
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
            QuickEntryService quickEntryService,
            BackupService backupService,
            ExportService exportService,
            PaymentCardService paymentCardService,
            CurrencyService currencyService) {
        this(expenseService, analyticsService, budgetService, categoryService,
                accountService, incomeService, transferService, financeService,
                recurringService, quickEntryService, backupService, exportService,
                paymentCardService, currencyService, null, null, null, null,
                null, null, null, null);
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
            QuickEntryService quickEntryService,
            BackupService backupService,
            ExportService exportService,
            PaymentCardService paymentCardService,
            CurrencyService currencyService,
            AdvancedBudgetService advancedBudgetService,
            SavingsGoalService savingsGoalService,
            DebtService debtService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            FinanceNotificationService notificationService,
            JsonBackupService jsonBackupService,
            CsvImportService csvImportService,
            PdfReportService pdfReportService) {
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
        Objects.requireNonNull(backupService, "Backup service is required.");
        Objects.requireNonNull(exportService, "Export service is required.");
        FinancialReportingService reportingService =
                new FinancialReportingService(
                        expenseService,
                        incomeService,
                        transferService,
                        accountService,
                        budgetService);
        AccountStatementService accountStatementService =
                new AccountStatementService(
                        accountService,
                        expenseService,
                        incomeService,
                        transferService,
                        financeService);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        DashboardPanel analyticsPanel =
                new DashboardPanel(analyticsService, budgetService);
        BudgetPanel budgetPanel =
                new BudgetPanel(
                        analyticsService, budgetService, categoryService);
        CalendarPanel calendarPanel = new CalendarPanel(reportingService);
        AdvancedReportsPanel reportsPanel = portfolioAnalyticsService == null
                ? new AdvancedReportsPanel(
                        reportingService, accountService, categoryService)
                : new AdvancedReportsPanel(reportingService, accountService,
                        categoryService, portfolioAnalyticsService);
        final ExpensePanel[] expenseReference = new ExpensePanel[1];
        final FinancePanel[] financeReference = new FinancePanel[1];
        final RecurringPanel[] recurringReference = new RecurringPanel[1];
        final OverviewPanel[] overviewReference = new OverviewPanel[1];
        final TransactionsPanel[] transactionsReference =
                new TransactionsPanel[1];
        final QuickEntryDialog[] quickEntryReference = new QuickEntryDialog[1];
        FinancePanel financePanel = new FinancePanel(
                accountService,
                incomeService,
                transferService,
                financeService,
                accountStatementService,
                () -> {
                    if (expenseReference[0] != null) {
                        expenseReference[0].refreshExpenses();
                    }
                    analyticsPanel.refreshDashboard();
                    budgetPanel.refreshBudgetStatus();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                    if (overviewReference[0] != null) {
                        overviewReference[0].refreshOverview();
                    }
                    if (transactionsReference[0] != null) {
                        transactionsReference[0].refreshTransactions();
                    }
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
                    analyticsPanel.refreshDashboard();
                    budgetPanel.refreshBudgetStatus();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                    if (overviewReference[0] != null) {
                        overviewReference[0].refreshOverview();
                    }
                    if (transactionsReference[0] != null) {
                        transactionsReference[0].refreshTransactions();
                    }
                },
                () -> {
                    financePanel.refreshFinanceData();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                });
        expenseReference[0] = expensePanel;
        OverviewPanel overviewPanel = new OverviewPanel(
                financeService,
                expenseService,
                incomeService,
                transferService,
                analyticsService,
                budgetService,
                recurringService,
                currencyService);
        overviewReference[0] = overviewPanel;
        TransactionsPanel transactionsPanel = new TransactionsPanel(
                expenseService,
                incomeService,
                transferService,
                accountService,
                categoryService,
                () -> {
                    expensePanel.refreshExpenses();
                    financePanel.refreshFinanceData();
                    overviewPanel.refreshOverview();
                    analyticsPanel.refreshDashboard();
                    budgetPanel.refreshBudgetStatus();
                    calendarPanel.refreshCalendar();
                    reportsPanel.refreshReports();
                });
        transactionsReference[0] = transactionsPanel;
        Runnable refreshFinancialViews = () -> {
            expensePanel.refreshExpenses();
            financePanel.refreshFinanceData();
            transactionsPanel.refreshTransactions();
            overviewPanel.refreshOverview();
            analyticsPanel.refreshDashboard();
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

        AppShellPanel shell = new AppShellPanel(quickEntryDialog::open);
        shell.addPage("overview", "Overview", AppIcons.Type.DASHBOARD,
                overviewPanel, overviewPanel::refreshOverview);
        shell.addPage("transactions", "Transactions",
                AppIcons.Type.TRANSACTIONS, transactionsPanel,
                transactionsPanel::refreshTransactions);
        shell.addPage("expenses", "Expenses", AppIcons.Type.EXPENSES,
                expensePanel, expensePanel::refreshExpenses);
        shell.addPage("finance", "Accounts & Finance", AppIcons.Type.FINANCE,
                financePanel, financePanel::refreshFinanceData);
        if (paymentCardService != null && currencyService != null) {
            CardsPanel cardsPanel = new CardsPanel(
                    paymentCardService, accountService, currencyService,
                    overviewPanel::refreshOverview);
            shell.addPage("cards", "Cards & Currency", AppIcons.Type.CARDS,
                    cardsPanel, cardsPanel::refreshCards);
        }
        shell.addPage("budgets", "Budgets", AppIcons.Type.BUDGETS,
                budgetPanel, budgetPanel::refreshBudgetStatus);
        shell.addPage("calendar", "Calendar", AppIcons.Type.CALENDAR,
                calendarPanel, calendarPanel::refreshCalendar);
        shell.addPage("reports", "Reports", AppIcons.Type.REPORTS,
                reportsPanel, reportsPanel::refreshReports);
        shell.addPage("recurring", "Recurring", AppIcons.Type.RECURRING,
                recurringPanel, recurringPanel::refreshRecurringEntries);
        if (advancedBudgetService != null && savingsGoalService != null
                && debtService != null) {
            PlanningPanel planningPanel = new PlanningPanel(
                    advancedBudgetService, savingsGoalService, debtService,
                    accountService, categoryService);
            shell.addPage("planning", "Planning", AppIcons.Type.PLANNING,
                    planningPanel, planningPanel::refreshPlanning);
        }
        if (notificationService != null) {
            NotificationCenterPanel notificationPanel =
                    new NotificationCenterPanel(notificationService);
            shell.addPage("notifications", "Notifications",
                    AppIcons.Type.NOTIFICATIONS, notificationPanel,
                    notificationPanel::refreshNotifications);
        }
        shell.addPage("analytics", "Expense Analytics",
                AppIcons.Type.ANALYTICS, analyticsPanel,
                analyticsPanel::refreshDashboard);
        shell.setGlobalSearchListener(query -> {
            shell.showPage("transactions");
            transactionsPanel.setSearchText(query);
        });
        JMenuBar menuBar = new JMenuBar();
        JMenu entryMenu = new JMenu("Entry");
        entryMenu.setMnemonic('E');
        JMenuItem quickEntryItem = new JMenuItem("Quick Entry");
        quickEntryItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        quickEntryItem.addActionListener(event -> quickEntryDialog.open());
        entryMenu.add(quickEntryItem);
        JMenuItem searchItem = new JMenuItem("Search Transactions");
        searchItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK));
        searchItem.addActionListener(event -> shell.focusGlobalSearch());
        entryMenu.add(searchItem);
        menuBar.add(entryMenu);
        DataManagementActions dataActions = new DataManagementActions(this,
                backupService, exportService, reportsPanel::getLatestSnapshot,
                refreshFinancialViews, jsonBackupService, csvImportService,
                pdfReportService, reportsPanel::getLatestPortfolioSnapshot,
                () -> currencyService == null ? "BDT"
                        : currencyService.getCurrency().getCurrencyCode());
        menuBar.add(dataActions.createMenu());
        setJMenuBar(menuBar);
        setContentPane(shell);
        setSize(1320, 820);
        setMinimumSize(new Dimension(980, 640));
        getRootPane().registerKeyboardAction(
                event -> shell.focusGlobalSearch(),
                KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        AppTheme.applyCustomColors(this);
        setLocationRelativeTo(null);
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "SpendWiseFrame must be created on the Event Dispatch Thread.");
        }
    }
}
