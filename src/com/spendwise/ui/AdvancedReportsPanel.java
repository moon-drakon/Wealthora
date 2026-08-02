package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AccountActivitySummary;
import com.spendwise.service.AccountService;
import com.spendwise.service.AdvancedReportSnapshot;
import com.spendwise.service.BudgetActualSummary;
import com.spendwise.service.CategoryService;
import com.spendwise.service.FinancialReportingService;
import com.spendwise.service.MonthlyCashFlowSummary;
import com.spendwise.service.PortfolioAnalyticsService;
import com.spendwise.service.PortfolioAnalyticsSnapshot;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public final class AdvancedReportsPanel extends JPanel {

    private static final Color PAGE_BACKGROUND = new Color(244, 247, 250);

    private final FinancialReportingService reportingService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final PortfolioAnalyticsService portfolioService;
    private final JTextField startDateField = new JTextField(10);
    private final JTextField endDateField = new JTextField(10);
    private final JComboBox<FilterChoice<Account>> accountFilter =
            new JComboBox<>();
    private final JComboBox<FilterChoice<Category>> categoryFilter =
            new JComboBox<>();
    private final JLabel incomeValue = metricValue();
    private final JLabel expenseValue = metricValue();
    private final JLabel netValue = metricValue();
    private final JLabel netWorthValue = metricValue();
    private final JLabel borrowedValue = metricValue();
    private final JLabel recurringValue = metricValue();
    private final JLabel statusLabel = new JLabel("Loading advanced report...");
    private final ReportTableModel categoryModel = new ReportTableModel(
            new String[]{"Rank", "Category", "Expenses"},
            new Class<?>[]{Integer.class, String.class, BigDecimal.class});
    private final ReportTableModel sourceModel = new ReportTableModel(
            new String[]{"Income Source", "Income"},
            new Class<?>[]{String.class, BigDecimal.class});
    private final ReportTableModel accountModel = new ReportTableModel(
            new String[]{"Account", "Income", "Expenses", "Incoming", "Outgoing", "Net"},
            new Class<?>[]{String.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class});
    private final ReportTableModel trendModel = new ReportTableModel(
            new String[]{"Month", "Income", "Expenses", "Net"},
            new Class<?>[]{String.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class});
    private final ReportTableModel budgetModel = new ReportTableModel(
            new String[]{"Month", "Scope", "Limit", "Actual", "Remaining"},
            new Class<?>[]{String.class, String.class, Object.class,
                BigDecimal.class, Object.class});

    private AdvancedReportSnapshot latestSnapshot;
    private PortfolioAnalyticsSnapshot latestPortfolioSnapshot;

    public AdvancedReportsPanel(
            FinancialReportingService reportingService,
            AccountService accountService,
            CategoryService categoryService) {
        this(reportingService, accountService, categoryService, null);
    }

    public AdvancedReportsPanel(
            FinancialReportingService reportingService,
            AccountService accountService,
            CategoryService categoryService,
            PortfolioAnalyticsService portfolioService) {
        requireEventDispatchThread();
        this.reportingService = Objects.requireNonNull(
                reportingService, "Financial reporting service is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.categoryService = Objects.requireNonNull(
                categoryService, "Category service is required.");
        this.portfolioService = portfolioService;
        LocalDate today = LocalDate.now();
        startDateField.setText(today.withDayOfMonth(1).toString());
        endDateField.setText(today.toString());
        refreshFilterChoices();
        buildInterface();
        refreshReports();
    }

    public void refreshReports() {
        requireEventDispatchThread();
        try {
            refreshFilterChoices();
            LocalDate startDate = LocalDate.parse(startDateField.getText().strip());
            LocalDate endDate = LocalDate.parse(endDateField.getText().strip());
            Account selectedAccount = selectedValue(accountFilter);
            Category selectedCategory = selectedValue(categoryFilter);
            AdvancedReportSnapshot snapshot = reportingService.buildAdvancedReport(
                    startDate, endDate, selectedAccount, selectedCategory);
            applySnapshot(snapshot);
            if (portfolioService != null) {
                applyPortfolio(portfolioService.build(startDate, endDate,
                        selectedAccount, selectedCategory, LocalDate.now()));
            }
        } catch (DateTimeParseException exception) {
            statusLabel.setText(
                    "Report dates must use yyyy-MM-dd.");
        } catch (ValidationException | RepositoryException exception) {
            statusLabel.setText(
                    "Report refresh failed: " + safeMessage(exception));
        }
    }

    AdvancedReportSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    PortfolioAnalyticsSnapshot getLatestPortfolioSnapshot() {
        return latestPortfolioSnapshot;
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    int getCategoryRowCount() {
        return categoryModel.getRowCount();
    }

    int getAccountRowCount() {
        return accountModel.getRowCount();
    }

    void setDateRange(LocalDate startDate, LocalDate endDate) {
        startDateField.setText(Objects.requireNonNull(startDate).toString());
        endDateField.setText(Objects.requireNonNull(endDate).toString());
    }

    private void refreshFilterChoices() {
        Account selectedAccount = selectedValue(accountFilter);
        Category selectedCategory = selectedValue(categoryFilter);
        accountFilter.removeAllItems();
        accountFilter.addItem(new FilterChoice<>(null, "All Accounts"));
        for (Account account : accountService.listAllAccounts()) {
            accountFilter.addItem(new FilterChoice<>(
                    account, account.getDisplayName()));
        }
        selectValue(accountFilter, selectedAccount);
        categoryFilter.removeAllItems();
        categoryFilter.addItem(new FilterChoice<>(null, "All Categories"));
        for (Category category : categoryService.listAllCategories()) {
            categoryFilter.addItem(new FilterChoice<>(
                    category,
                    category.getDisplayName()
                    + (category.isArchived() ? " (Archived)" : "")));
        }
        selectValue(categoryFilter, selectedCategory);
    }

    private void buildInterface() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(PAGE_BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Advanced Financial Reports");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        header.add(title, BorderLayout.WEST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("From"));
        filters.add(startDateField);
        filters.add(new JLabel("To"));
        filters.add(endDateField);
        filters.add(accountFilter);
        filters.add(categoryFilter);
        JButton runButton = new JButton("Run Report");
        runButton.addActionListener(event -> refreshReports());
        filters.add(runButton);
        header.add(filters, BorderLayout.EAST);

        JPanel summary = new JPanel(new GridLayout(
                portfolioService == null ? 1 : 2, 3, 10, 10));
        summary.setOpaque(false);
        summary.add(metricCard("Income", incomeValue));
        summary.add(metricCard("Expenses", expenseValue));
        summary.add(metricCard("Net Cash Flow", netValue));
        if (portfolioService != null) {
            summary.add(metricCard("Net Worth", netWorthValue));
            summary.add(metricCard("Outstanding Borrowed", borrowedValue));
            summary.add(metricCard("Recurring Expenses", recurringValue));
        }

        JTabbedPane detailTabs = new JTabbedPane();
        detailTabs.addTab("Expense Categories", table(categoryModel));
        detailTabs.addTab("Income Sources", table(sourceModel));
        detailTabs.addTab("Account Activity", table(accountModel));
        detailTabs.addTab("Monthly Trend", table(trendModel));
        detailTabs.addTab("Budget vs Actual", table(budgetModel));

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(summary, BorderLayout.NORTH);
        center.add(detailTabs, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void applySnapshot(AdvancedReportSnapshot snapshot) {
        latestSnapshot = snapshot;
        incomeValue.setText(snapshot.getTotalIncome().toPlainString());
        expenseValue.setText(snapshot.getTotalExpenses().toPlainString());
        netValue.setText(snapshot.getNetCashFlow().toPlainString());

        List<Object[]> categoryRows = new ArrayList<>();
        int rank = 1;
        for (Category category : snapshot.getHighestExpenseCategories()) {
            categoryRows.add(new Object[]{
                rank++,
                category.getDisplayName(),
                snapshot.getExpensesByCategory().get(category)
            });
        }
        categoryModel.replaceRows(categoryRows);

        List<Object[]> sourceRows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry
                : snapshot.getIncomeBySource().entrySet()) {
            sourceRows.add(new Object[]{entry.getKey(), entry.getValue()});
        }
        sourceModel.replaceRows(sourceRows);

        List<Object[]> accountRows = new ArrayList<>();
        for (AccountActivitySummary activity
                : snapshot.getAccountActivity().values()) {
            accountRows.add(new Object[]{
                activity.getAccount().getDisplayName(),
                activity.getIncome(),
                activity.getExpenses(),
                activity.getIncomingTransfers(),
                activity.getOutgoingTransfers(),
                activity.getNetActivity()
            });
        }
        accountModel.replaceRows(accountRows);

        List<Object[]> trendRows = new ArrayList<>();
        for (MonthlyCashFlowSummary month : snapshot.getMonthlyTrend().values()) {
            trendRows.add(new Object[]{
                month.getMonth().toString(),
                month.getIncome(),
                month.getExpenses(),
                month.getNetCashFlow()
            });
        }
        trendModel.replaceRows(trendRows);

        List<Object[]> budgetRows = new ArrayList<>();
        for (BudgetActualSummary budget : snapshot.getBudgetActuals()) {
            Object overallLimit = budget.getOverallLimit()
                    .<Object>map(value -> value)
                    .orElse("Not set");
            Object remaining = budget.getOverallLimit()
                    .<Object>map(limit -> limit.subtract(
                        budget.getActualExpenses()))
                    .orElse("Not set");
            budgetRows.add(new Object[]{
                budget.getMonth().toString(),
                "Overall",
                overallLimit,
                budget.getActualExpenses(),
                remaining
            });
            for (Map.Entry<Category, BigDecimal> limit
                    : budget.getCategoryLimits().entrySet()) {
                BigDecimal actual = budget.getCategoryActuals()
                        .getOrDefault(limit.getKey(), new BigDecimal("0.00"));
                budgetRows.add(new Object[]{
                    budget.getMonth().toString(),
                    limit.getKey().getDisplayName(),
                    limit.getValue(),
                    actual,
                    limit.getValue().subtract(actual)
                });
            }
        }
        budgetModel.replaceRows(budgetRows);

        statusLabel.setText(
                snapshot.getStartDate() + " through " + snapshot.getEndDate()
                + " · " + categoryRows.size() + " expense categories · "
                + "Transfers excluded from income and expense totals.");
    }

    private void applyPortfolio(PortfolioAnalyticsSnapshot snapshot) {
        latestPortfolioSnapshot = snapshot;
        netWorthValue.setText(snapshot.netWorth().toPlainString());
        borrowedValue.setText(snapshot.outstandingBorrowed().toPlainString());
        recurringValue.setText(snapshot.recurringCommitments()
                .scheduledExpenses().toPlainString());
    }

    private static JScrollPane table(ReportTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(23);
        table.getTableHeader().setReorderingAllowed(false);
        return new JScrollPane(table);
    }

    private static JPanel metricCard(String title, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel metricValue() {
        JLabel label = new JLabel("0.00");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        return label;
    }

    private static <T> T selectedValue(
            JComboBox<FilterChoice<T>> comboBox) {
        @SuppressWarnings("unchecked")
        FilterChoice<T> choice =
                (FilterChoice<T>) comboBox.getSelectedItem();
        return choice == null ? null : choice.value();
    }

    private static <T> void selectValue(
            JComboBox<FilterChoice<T>> comboBox, T selectedValue) {
        if (selectedValue == null) {
            comboBox.setSelectedIndex(0);
            return;
        }
        for (int index = 0; index < comboBox.getItemCount(); index++) {
            FilterChoice<T> choice = comboBox.getItemAt(index);
            if (Objects.equals(choice.value(), selectedValue)) {
                comboBox.setSelectedIndex(index);
                return;
            }
        }
        comboBox.setSelectedIndex(0);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The report could not be generated safely."
                : message;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "AdvancedReportsPanel must be used on the Event Dispatch Thread.");
        }
    }

    private record FilterChoice<T>(T value, String label) {

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ReportTableModel extends AbstractTableModel {

        private final String[] columns;
        private final Class<?>[] columnClasses;
        private List<Object[]> rows = List.of();

        private ReportTableModel(
                String[] columns, Class<?>[] columnClasses) {
            this.columns = columns.clone();
            this.columnClasses = columnClasses.clone();
        }

        private void replaceRows(List<Object[]> newRows) {
            List<Object[]> copiedRows = new ArrayList<>();
            for (Object[] row : newRows) {
                copiedRows.add(row.clone());
            }
            rows = List.copyOf(copiedRows);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return columnClasses[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            return rows.get(row)[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
