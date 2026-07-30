package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseAnalyticsSnapshot;
import com.spendwise.service.ExpenseSummary;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

public final class DashboardPanel extends JPanel {

    private static final Color PAGE_BACKGROUND = new Color(244, 247, 250);
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color PRIMARY_COLOR = new Color(42, 92, 130);
    private static final Color TEXT_COLOR = new Color(47, 58, 68);
    private static final Color SECONDARY_TEXT = new Color(80, 90, 100);
    private static final Color INCREASE_COLOR = new Color(176, 91, 60);
    private static final Color DECREASE_COLOR = new Color(43, 125, 113);
    private static final Color NEUTRAL_COLOR = new Color(85, 96, 106);

    private final ExpenseAnalyticsService analyticsService;
    private final JComboBox<Month> monthComboBox = new JComboBox<>(Month.values());
    private final JSpinner yearSpinner;
    private final JLabel monthlyCountValue = createSummaryValueLabel();
    private final JLabel monthlyTotalValue = createSummaryValueLabel();
    private final JLabel monthlyAverageValue = createSummaryValueLabel();
    private final JLabel monthlyChangeValue = createSummaryValueLabel();
    private final MonthlyBarChartPanel monthlyBarChart = new MonthlyBarChartPanel();
    private final CategoryDonutChartPanel categoryDonutChart =
            new CategoryDonutChartPanel();
    private final ExpenseTableModel reportExpenseTableModel = new ExpenseTableModel();
    private final CategoryBreakdownTableModel categoryTableModel =
            new CategoryBreakdownTableModel();
    private final JTable reportExpenseTable = new JTable(reportExpenseTableModel);
    private final JTable categoryTable = new JTable(categoryTableModel);
    private final JLabel reportTitle = new JLabel("Monthly Report");
    private final JLabel reportCountValue = new JLabel("0");
    private final JLabel reportTotalValue = new JLabel("0.00");
    private final JLabel reportAverageValue = new JLabel("0.00");
    private final JLabel reportPreviousValue = new JLabel("0.00");
    private final JLabel reportChangeValue = new JLabel("0.00");
    private final JLabel statusLabel = new JLabel("Loading dashboard...");

    private YearMonth displayedMonth;

    public DashboardPanel(ExpenseAnalyticsService analyticsService) {
        this(analyticsService, YearMonth.now());
    }

    DashboardPanel(
            ExpenseAnalyticsService analyticsService, YearMonth initialMonth) {
        requireEventDispatchThread();
        this.analyticsService = Objects.requireNonNull(
                analyticsService, "Expense analytics service is required.");
        YearMonth requiredInitialMonth = Objects.requireNonNull(
                initialMonth, "Initial dashboard month is required.");
        yearSpinner = new JSpinner(new SpinnerNumberModel(
                requiredInitialMonth.getYear(), 1, 9999, 1));
        yearSpinner.setEditor(new JSpinner.NumberEditor(yearSpinner, "0"));
        monthComboBox.setSelectedItem(requiredInitialMonth.getMonth());

        configureControls();
        buildInterface();
        refreshDashboard(false);
    }

    public void refreshDashboard() {
        requireEventDispatchThread();
        refreshDashboard(true);
    }

    YearMonth getDisplayedMonth() {
        return displayedMonth;
    }

    String getMonthlyCountText() {
        return monthlyCountValue.getText();
    }

    String getMonthlyTotalText() {
        return monthlyTotalValue.getText();
    }

    String getMonthlyAverageText() {
        return monthlyAverageValue.getText();
    }

    String getMonthlyChangeText() {
        return monthlyChangeValue.getText();
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    int getReportExpenseRowCount() {
        return reportExpenseTableModel.getRowCount();
    }

    String getReportExpenseId(int rowIndex) {
        return reportExpenseTableModel.getExpenseAt(rowIndex).getId();
    }

    boolean isReportExpenseCellEditable(int rowIndex, int columnIndex) {
        return reportExpenseTableModel.isCellEditable(rowIndex, columnIndex);
    }

    int getCategoryRowCount() {
        return categoryTableModel.getRowCount();
    }

    Category getCategoryAt(int rowIndex) {
        return categoryTableModel.getCategoryAt(rowIndex);
    }

    BigDecimal getCategoryTotalAt(int rowIndex) {
        return categoryTableModel.getTotalAt(rowIndex);
    }

    Map<YearMonth, BigDecimal> getBarChartData() {
        return monthlyBarChart.getDataSnapshot();
    }

    Map<Category, BigDecimal> getDonutChartData() {
        return categoryDonutChart.getDataSnapshot();
    }

    private void configureControls() {
        monthComboBox.setRenderer(new MonthRenderer());
        monthComboBox.getAccessibleContext().setAccessibleName("Dashboard month");
        yearSpinner.getAccessibleContext().setAccessibleName("Dashboard year");

        JFormattedTextField yearField =
                ((JSpinner.DefaultEditor) yearSpinner.getEditor()).getTextField();
        yearField.setColumns(5);
        yearField.addActionListener(event -> refreshDashboard());
    }

    private void buildInterface() {
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        setBackground(PAGE_BACKGROUND);

        add(createHeaderArea(), BorderLayout.NORTH);
        add(createDashboardBody(), BorderLayout.CENTER);

        statusLabel.setForeground(SECONDARY_TEXT);
        statusLabel.getAccessibleContext().setAccessibleDescription(
                "Dashboard refresh status");
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createHeaderArea() {
        JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Expense Analytics Dashboard");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 25f));
        titleLabel.setForeground(PRIMARY_COLOR);

        JLabel subtitleLabel = new JLabel(
                "Analyze monthly spending, recent trends, and category totals.");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(13f));
        subtitleLabel.setForeground(SECONDARY_TEXT);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitleLabel);

        JPanel selectorPanel = createSelectorPanel();
        headerPanel.add(titlePanel, BorderLayout.CENTER);
        headerPanel.add(selectorPanel, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel createSelectorPanel() {
        JPanel selectorPanel = new JPanel(new GridBagLayout());
        selectorPanel.setOpaque(false);

        JLabel monthLabel = new JLabel("Month");
        monthLabel.setLabelFor(monthComboBox);
        JLabel yearLabel = new JLabel("Year");
        yearLabel.setLabelFor(yearSpinner);
        JButton refreshButton = new JButton("Refresh Dashboard");
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.BOLD));
        refreshButton.addActionListener(event -> refreshDashboard());
        refreshButton.getAccessibleContext().setAccessibleDescription(
                "Refresh analytics for the selected month");

        addSelectorComponent(selectorPanel, monthLabel, 0, 0, new Insets(0, 0, 3, 8));
        addSelectorComponent(selectorPanel, yearLabel, 1, 0, new Insets(0, 0, 3, 8));
        addSelectorComponent(
                selectorPanel, monthComboBox, 0, 1, new Insets(0, 0, 0, 8));
        addSelectorComponent(selectorPanel, yearSpinner, 1, 1, new Insets(0, 0, 0, 8));
        addSelectorComponent(selectorPanel, refreshButton, 2, 1, new Insets(0, 2, 0, 0));
        return selectorPanel;
    }

    private void addSelectorComponent(
            JPanel panel,
            Component component,
            int column,
            int row,
            Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = insets;
        panel.add(component, constraints);
    }

    private JPanel createDashboardBody() {
        JPanel bodyPanel = new JPanel();
        bodyPanel.setOpaque(false);
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));

        JPanel cardsPanel = new JPanel(new GridLayout(1, 4, 12, 0));
        cardsPanel.setOpaque(false);
        cardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardsPanel.add(createSummaryCard("Monthly Expenses", monthlyCountValue));
        cardsPanel.add(createSummaryCard("Monthly Total", monthlyTotalValue));
        cardsPanel.add(createSummaryCard("Monthly Average", monthlyAverageValue));
        cardsPanel.add(createSummaryCard(
                "Change from Previous Month", monthlyChangeValue));

        JTabbedPane dashboardTabs = new JTabbedPane();
        dashboardTabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        dashboardTabs.addTab("Overview", createOverviewPanel());
        dashboardTabs.addTab("Monthly Report", createReportPanel());

        bodyPanel.add(cardsPanel);
        bodyPanel.add(Box.createVerticalStrut(14));
        bodyPanel.add(dashboardTabs);
        return bodyPanel;
    }

    private JPanel createSummaryCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BACKGROUND);
        card.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(13, 15, 13, 15)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(SECONDARY_TEXT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(7));
        card.add(valueLabel);
        return card;
    }

    private JPanel createOverviewPanel() {
        JPanel overviewPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        overviewPanel.setBackground(PAGE_BACKGROUND);
        overviewPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        overviewPanel.add(createChartCard(monthlyBarChart));
        overviewPanel.add(createChartCard(categoryDonutChart));
        return overviewPanel;
    }

    private JPanel createChartCard(JPanel chartPanel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BACKGROUND);
        card.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        card.add(chartPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createReportPanel() {
        JPanel reportPanel = new JPanel(new BorderLayout(0, 12));
        reportPanel.setBackground(PAGE_BACKGROUND);
        reportPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        JPanel reportHeader = new JPanel();
        reportHeader.setBackground(CARD_BACKGROUND);
        reportHeader.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        reportHeader.setLayout(new BoxLayout(reportHeader, BoxLayout.Y_AXIS));
        reportTitle.setFont(reportTitle.getFont().deriveFont(Font.BOLD, 18f));
        reportTitle.setForeground(PRIMARY_COLOR);
        reportTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportHeader.add(reportTitle);
        reportHeader.add(Box.createVerticalStrut(9));
        reportHeader.add(createReportSummaryRow());

        configureCategoryTable();
        configureReportExpenseTable();
        JScrollPane categoryScrollPane = new JScrollPane(categoryTable);
        categoryScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(210, 218, 225)),
                "Category Breakdown"));
        categoryScrollPane.setPreferredSize(new Dimension(330, 270));

        JScrollPane expenseScrollPane = new JScrollPane(reportExpenseTable);
        expenseScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(210, 218, 225)),
                "Selected-Month Expenses"));

        JSplitPane tablesSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                categoryScrollPane,
                expenseScrollPane);
        tablesSplitPane.setResizeWeight(0.3);
        tablesSplitPane.setBorder(null);
        tablesSplitPane.setContinuousLayout(true);

        reportPanel.add(reportHeader, BorderLayout.NORTH);
        reportPanel.add(tablesSplitPane, BorderLayout.CENTER);
        return reportPanel;
    }

    private JPanel createReportSummaryRow() {
        JPanel summaryPanel = new JPanel(new GridLayout(1, 5, 18, 0));
        summaryPanel.setOpaque(false);
        summaryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryPanel.add(createReportMetric("Expenses", reportCountValue));
        summaryPanel.add(createReportMetric("Total", reportTotalValue));
        summaryPanel.add(createReportMetric("Average", reportAverageValue));
        summaryPanel.add(createReportMetric("Previous Month", reportPreviousValue));
        summaryPanel.add(createReportMetric("Change", reportChangeValue));
        return summaryPanel;
    }

    private JPanel createReportMetric(String labelText, JLabel valueLabel) {
        JPanel metricPanel = new JPanel();
        metricPanel.setOpaque(false);
        metricPanel.setLayout(new BoxLayout(metricPanel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(labelText);
        label.setForeground(SECONDARY_TEXT);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 14f));
        valueLabel.setForeground(TEXT_COLOR);
        metricPanel.add(label);
        metricPanel.add(Box.createVerticalStrut(3));
        metricPanel.add(valueLabel);
        return metricPanel;
    }

    private void configureCategoryTable() {
        categoryTable.setFillsViewportHeight(true);
        categoryTable.setRowHeight(23);
        categoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryTable.getTableHeader().setReorderingAllowed(false);
        categoryTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        categoryTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        categoryTable.getColumnModel().getColumn(1)
                .setCellRenderer(new AmountCellRenderer());
    }

    private void configureReportExpenseTable() {
        reportExpenseTable.setFillsViewportHeight(true);
        reportExpenseTable.setRowHeight(23);
        reportExpenseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reportExpenseTable.setAutoCreateRowSorter(false);
        reportExpenseTable.getTableHeader().setReorderingAllowed(false);

        TableColumnModel columns = reportExpenseTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(90);
        columns.getColumn(1).setPreferredWidth(180);
        columns.getColumn(2).setPreferredWidth(110);
        columns.getColumn(3).setPreferredWidth(100);
        columns.getColumn(4).setPreferredWidth(260);
        columns.getColumn(3).setCellRenderer(new AmountCellRenderer());
    }

    private void refreshDashboard(boolean showFailureDialog) {
        YearMonth selectedMonth;
        try {
            yearSpinner.commitEdit();
            selectedMonth = selectedYearMonth();
        } catch (ParseException | RuntimeException exception) {
            handleRefreshFailure(
                    "Enter a year from 1 through 9999.",
                    showFailureDialog);
            return;
        }

        try {
            ExpenseAnalyticsSnapshot snapshot =
                    analyticsService.analyzeMonth(selectedMonth);
            DashboardViewData viewData = DashboardViewData.from(snapshot);
            applyViewData(viewData);
        } catch (ValidationException | RepositoryException exception) {
            handleRefreshFailure(safeMessage(exception), showFailureDialog);
        }
    }

    private YearMonth selectedYearMonth() {
        Month selectedMonth = (Month) monthComboBox.getSelectedItem();
        if (selectedMonth == null) {
            throw new ValidationException("Dashboard month is required.");
        }
        int selectedYear = ((Number) yearSpinner.getValue()).intValue();
        return YearMonth.of(selectedYear, selectedMonth);
    }

    private void applyViewData(DashboardViewData viewData) {
        ExpenseAnalyticsSnapshot snapshot = viewData.snapshot();
        displayedMonth = snapshot.getSelectedMonth();
        monthlyCountValue.setText(viewData.countText());
        monthlyTotalValue.setText(viewData.totalText());
        monthlyAverageValue.setText(viewData.averageText());
        monthlyChangeValue.setText(viewData.changeText());
        updateChangeColors(snapshot.getChangeFromPreviousMonth());

        monthlyBarChart.replaceData(snapshot.getMonthlyTotals());
        categoryDonutChart.replaceData(
                snapshot.getSelectedMonthSummary().getTotalsByCategory());
        categoryTableModel.replaceTotals(
                snapshot.getSelectedMonthSummary().getTotalsByCategory());
        reportExpenseTableModel.replaceExpenses(snapshot.getSelectedMonthExpenses());

        reportTitle.setText(viewData.reportTitle());
        reportCountValue.setText(viewData.countText());
        reportTotalValue.setText(viewData.totalText());
        reportAverageValue.setText(viewData.averageText());
        reportPreviousValue.setText(viewData.previousText());
        reportChangeValue.setText(viewData.changeText());
        statusLabel.setText(viewData.statusText());
    }

    private void updateChangeColors(BigDecimal change) {
        Color changeColor = change.compareTo(BigDecimal.ZERO) > 0
                ? INCREASE_COLOR
                : change.compareTo(BigDecimal.ZERO) < 0
                        ? DECREASE_COLOR
                        : NEUTRAL_COLOR;
        monthlyChangeValue.setForeground(changeColor);
        reportChangeValue.setForeground(changeColor);
    }

    private void handleRefreshFailure(String message, boolean showFailureDialog) {
        String safeMessage = message == null || message.isBlank()
                ? "Dashboard analytics could not be refreshed safely."
                : message;
        statusLabel.setText("Dashboard refresh failed: " + safeMessage);
        if (showFailureDialog && !GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(
                    this,
                    safeMessage,
                    "Unable to Refresh Dashboard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Dashboard analytics could not be refreshed safely."
                : message;
    }

    private static String signedMoney(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) > 0
                ? "+" + amount.toPlainString()
                : amount.toPlainString();
    }

    private static JLabel createSummaryValueLabel() {
        JLabel label = new JLabel("0.00");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        label.setForeground(PRIMARY_COLOR);
        return label;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "DashboardPanel must be created and updated on the Event Dispatch Thread.");
        }
    }

    private record DashboardViewData(
            ExpenseAnalyticsSnapshot snapshot,
            String countText,
            String totalText,
            String averageText,
            String previousText,
            String changeText,
            String reportTitle,
            String statusText) {

        static DashboardViewData from(ExpenseAnalyticsSnapshot snapshot) {
            ExpenseSummary summary = snapshot.getSelectedMonthSummary();
            String monthText = snapshot.getSelectedMonth()
                    .getMonth()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + " "
                    + snapshot.getSelectedMonth().getYear();
            int count = summary.getExpenseCount();
            return new DashboardViewData(
                    snapshot,
                    Integer.toString(count),
                    summary.getTotalAmount().toPlainString(),
                    summary.getAverageAmount().toPlainString(),
                    snapshot.getPreviousMonthTotal().toPlainString(),
                    signedMoney(snapshot.getChangeFromPreviousMonth()),
                    monthText + " Monthly Report",
                    monthText + " · " + count
                    + (count == 1 ? " expense" : " expenses")
                    + " · Dashboard refreshed.");
        }
    }

    private static final class CategoryBreakdownTableModel
            extends AbstractTableModel {

        private Map<Category, BigDecimal> totals = Map.of();

        CategoryBreakdownTableModel() {
            replaceTotals(Map.of());
        }

        void replaceTotals(Map<Category, BigDecimal> newTotals) {
            Objects.requireNonNull(newTotals, "Category totals are required.");
            java.util.EnumMap<Category, BigDecimal> copiedTotals =
                    new java.util.EnumMap<>(Category.class);
            for (Category category : Category.values()) {
                copiedTotals.put(
                        category,
                        Objects.requireNonNull(
                                newTotals.getOrDefault(
                                        category, new BigDecimal("0.00")),
                                "Category total is required."));
            }
            totals = java.util.Collections.unmodifiableMap(copiedTotals);
            fireTableDataChanged();
        }

        Category getCategoryAt(int rowIndex) {
            return Category.values()[rowIndex];
        }

        BigDecimal getTotalAt(int rowIndex) {
            return totals.get(getCategoryAt(rowIndex));
        }

        @Override
        public int getRowCount() {
            return Category.values().length;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int columnIndex) {
            return columnIndex == 0 ? "Category" : "Total";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? String.class : BigDecimal.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Category category = getCategoryAt(rowIndex);
            return columnIndex == 0
                    ? category.getDisplayName()
                    : totals.get(category);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private static final class MonthRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof Month month) {
                setText(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            }
            return this;
        }
    }

    private static final class AmountCellRenderer extends DefaultTableCellRenderer {

        AmountCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            setText(value instanceof BigDecimal amount
                    ? amount.toPlainString()
                    : "");
        }
    }
}
