package com.spendwise.ui;

import com.spendwise.model.RecurringEntry;
import com.spendwise.service.BudgetService;
import com.spendwise.service.BudgetUsage;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseAnalyticsSnapshot;
import com.spendwise.service.FinanceService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.RecurringService;
import com.spendwise.service.TransferService;
import com.spendwise.service.ExpenseService;
import com.spendwise.ui.component.EmptyStatePanel;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.component.SummaryCard;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;

public final class OverviewPanel extends JPanel {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int RECENT_LIMIT = 7;
    private static final int UPCOMING_LIMIT = 6;

    private final FinanceService financeService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final ExpenseAnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final RecurringService recurringService;
    private final SummaryCard balanceCard = new SummaryCard(
            "Total balance", AppColors.transfer());
    private final SummaryCard incomeCard = new SummaryCard(
            "Current-month income", AppColors.income());
    private final SummaryCard expenseCard = new SummaryCard(
            "Current-month expense", AppColors.expense());
    private final SummaryCard netCard = new SummaryCard(
            "Net cash flow", AppColors.accent());
    private final SummaryCard budgetCard = new SummaryCard(
            "Budget usage", AppColors.warning());
    private final TransactionTableModel recentModel = new TransactionTableModel();
    private final StyledTable recentTable = new StyledTable(recentModel);
    private final CardLayout recentLayout = new CardLayout();
    private final JPanel recentContent = new JPanel(recentLayout);
    private final DefaultListModel<RecurringEntry> recurringModel =
            new DefaultListModel<>();
    private final JList<RecurringEntry> recurringList = new JList<>(recurringModel);
    private final CardLayout recurringLayout = new CardLayout();
    private final JPanel recurringContent = new JPanel(recurringLayout);
    private final CategoryDonutChartPanel categoryChart =
            new CategoryDonutChartPanel();
    private final JLabel updatedLabel = new JLabel(" ");

    public OverviewPanel(
            FinanceService financeService,
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService,
            RecurringService recurringService) {
        super(new BorderLayout());
        this.financeService = Objects.requireNonNull(financeService);
        this.expenseService = Objects.requireNonNull(expenseService);
        this.incomeService = Objects.requireNonNull(incomeService);
        this.transferService = Objects.requireNonNull(transferService);
        this.analyticsService = Objects.requireNonNull(analyticsService);
        this.budgetService = Objects.requireNonNull(budgetService);
        this.recurringService = Objects.requireNonNull(recurringService);
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        buildInterface();
        refreshOverview();
    }

    public void refreshOverview() {
        YearMonth month = YearMonth.now();
        ExpenseAnalyticsSnapshot analytics = analyticsService.analyzeMonth(month);
        BigDecimal income = incomeService.getAllIncome().stream()
                .filter(item -> YearMonth.from(item.getDate()).equals(month))
                .map(com.spendwise.model.Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense =
                analytics.getSelectedMonthSummary().getTotalAmount();
        BigDecimal net = income.subtract(expense);

        balanceCard.setValue(money(
                financeService.calculateBalances().getTotalBalance()));
        balanceCard.setDetail("Across all accounts");
        incomeCard.setValue(money(income));
        incomeCard.setDetail(month.format(MONTH_LABEL));
        expenseCard.setValue(money(expense));
        expenseCard.setDetail(month.format(MONTH_LABEL));
        netCard.setValue(signedMoney(net));
        netCard.setDetail(net.signum() < 0
                ? "Expenses exceed income" : "Income after expenses");
        updateBudgetCard(budgetService.evaluate(analytics).getOverallUsage());
        categoryChart.replaceData(
                analytics.getSelectedMonthSummary().getTotalsByCategory());
        updateRecentActivity();
        updateUpcomingItems();
        updatedLabel.setText("Showing live local data for "
                + month.format(MONTH_LABEL));
        revalidate();
        repaint();
    }

    private void buildInterface() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        content.add(buildSummaryCards(), BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 14));
        details.setOpaque(false);
        details.add(buildActivityRow());
        details.add(buildChartCard());
        content.add(details, BorderLayout.CENTER);

        updatedLabel.setFont(AppFonts.caption());
        AppTheme.mark(updatedLabel, AppTheme.SECONDARY_TEXT_ROLE);
        content.add(updatedLabel, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getViewport().setBackground(AppColors.pageBackground());
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel buildSummaryCards() {
        JPanel cards = new JPanel(new GridLayout(0, 3, 12, 12));
        cards.setOpaque(false);
        cards.add(balanceCard);
        cards.add(incomeCard);
        cards.add(expenseCard);
        cards.add(netCard);
        cards.add(budgetCard);
        JPanel helper = new JPanel(new BorderLayout());
        AppTheme.mark(helper, AppTheme.CARD_ROLE);
        helper.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("Your private workspace");
        title.setFont(AppFonts.sectionTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel detail = new JLabel(
                "Stored locally. No account or cloud connection is active.");
        detail.setFont(AppFonts.caption());
        AppTheme.mark(detail, AppTheme.SECONDARY_TEXT_ROLE);
        helper.add(title, BorderLayout.NORTH);
        helper.add(detail, BorderLayout.CENTER);
        cards.add(helper);
        return cards;
    }

    private JPanel buildActivityRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.add(buildRecentCard());
        row.add(buildRecurringCard());
        return row;
    }

    private JPanel buildRecentCard() {
        JPanel card = sectionCard("Recent transactions");
        recentTable.setAutoCreateRowSorter(false);
        recentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentTable.setDefaultRenderer(BigDecimal.class,
                new RecentAmountRenderer());
        recentTable.getColumnModel().getColumn(3).setMinWidth(0);
        recentTable.getColumnModel().getColumn(3).setMaxWidth(0);
        recentTable.getColumnModel().getColumn(4).setPreferredWidth(135);
        JScrollPane scroll = new JScrollPane(recentTable);
        scroll.setBorder(BorderFactory.createLineBorder(AppColors.border()));
        recentContent.add(scroll, "table");
        recentContent.add(new EmptyStatePanel(
                "No activity yet",
                "Add an expense, income entry, or transfer to begin."), "empty");
        card.add(recentContent, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRecurringCard() {
        JPanel card = sectionCard("Upcoming recurring items");
        recurringList.setCellRenderer(new RecurringRenderer());
        recurringList.setFixedCellHeight(44);
        recurringList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(recurringList);
        scroll.setBorder(BorderFactory.createLineBorder(AppColors.border()));
        recurringContent.add(scroll, "list");
        recurringContent.add(new EmptyStatePanel(
                "Nothing scheduled",
                "Active recurring bills and income will appear here."), "empty");
        card.add(recurringContent, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildChartCard() {
        JPanel card = sectionCard("Category spending");
        categoryChart.setPreferredSize(new Dimension(720, 320));
        card.add(categoryChart, BorderLayout.CENTER);
        return card;
    }

    private static JPanel sectionCard(String titleText) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        AppTheme.mark(card, AppTheme.CARD_ROLE);
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel(titleText);
        title.setFont(AppFonts.sectionTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        card.add(title, BorderLayout.NORTH);
        return card;
    }

    private void updateBudgetCard(BudgetUsage usage) {
        if (usage.getLimit().isEmpty()) {
            budgetCard.setValue("Not set");
            budgetCard.setDetail("Spent " + money(usage.getSpent()));
            return;
        }
        budgetCard.setValue(usage.getUsagePercentage().orElseThrow()
                .stripTrailingZeros().toPlainString() + "%");
        budgetCard.setDetail(money(usage.getSpent()) + " of "
                + money(usage.getLimit().orElseThrow()));
    }

    private void updateRecentActivity() {
        List<TransactionRow> rows = new ArrayList<>();
        expenseService.getAllExpenses().stream()
                .map(TransactionRow::from).forEach(rows::add);
        incomeService.getAllIncome().stream()
                .map(TransactionRow::from).forEach(rows::add);
        transferService.getAllTransfers().stream()
                .map(TransactionRow::from).forEach(rows::add);
        List<TransactionRow> recent = rows.stream()
                .sorted(Comparator.comparing(TransactionRow::date).reversed()
                        .thenComparing(TransactionRow::identifier))
                .limit(RECENT_LIMIT)
                .toList();
        recentModel.setRows(recent);
        recentLayout.show(recentContent, recent.isEmpty() ? "empty" : "table");
    }

    private void updateUpcomingItems() {
        List<RecurringEntry> upcoming = recurringService.listAll().stream()
                .filter(RecurringEntry::isActive)
                .sorted(Comparator.comparing(RecurringEntry::getNextDueDate))
                .limit(UPCOMING_LIMIT)
                .toList();
        recurringModel.clear();
        upcoming.forEach(recurringModel::addElement);
        recurringLayout.show(
                recurringContent, upcoming.isEmpty() ? "empty" : "list");
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    private static String signedMoney(BigDecimal amount) {
        return amount.signum() > 0 ? "+" + money(amount) : money(amount);
    }

    private static final class RecentAmountRenderer
            extends DefaultTableCellRenderer {

        RecentAmountRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            TransactionRow item = ((TransactionTableModel) table.getModel())
                    .getRow(table.convertRowIndexToModel(row));
            setText(item.amountDisplay());
            if (!selected) {
                setForeground(switch (item.type()) {
                    case INCOME -> AppColors.income();
                    case EXPENSE -> AppColors.expense();
                    case TRANSFER -> AppColors.transfer();
                });
            }
            return this;
        }
    }

    private static final class RecurringRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean selected, boolean focused) {
            super.getListCellRendererComponent(
                    list, value, index, selected, focused);
            if (value instanceof RecurringEntry entry) {
                LocalDate due = entry.getNextDueDate();
                setText("<html><b>" + escape(entry.getDescription())
                        + "</b><br><span style='font-size:10px'>"
                        + entry.getType() + " · "
                        + entry.getAmount().toPlainString() + " · due "
                        + due + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            }
            return this;
        }

        private static String escape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
