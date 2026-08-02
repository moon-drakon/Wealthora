package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.BudgetPlan;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.Category;
import com.spendwise.model.DebtDirection;
import com.spendwise.model.SavingsGoal;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AccountService;
import com.spendwise.service.AdvancedBudgetService;
import com.spendwise.service.BudgetPlanStatus;
import com.spendwise.service.CategoryService;
import com.spendwise.service.DebtProgress;
import com.spendwise.service.DebtService;
import com.spendwise.service.SavingsGoalProgress;
import com.spendwise.service.SavingsGoalService;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public final class PlanningPanel extends JPanel {
    private final AdvancedBudgetService budgetService;
    private final SavingsGoalService goalService;
    private final DebtService debtService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final DataTableModel budgetModel = new DataTableModel(new String[]{
        "ID", "Name", "Period", "Limit", "Spent", "Usage", "State",
        "Rollover", "Status"});
    private final DataTableModel goalModel = new DataTableModel(new String[]{
        "ID", "Goal", "Account", "Target", "Contributed", "Progress",
        "Target Date", "Status"});
    private final DataTableModel debtModel = new DataTableModel(new String[]{
        "ID", "Direction", "Counterparty", "Original", "Repaid",
        "Remaining", "Due Date", "Status"});
    private final StyledTable budgetTable = table(budgetModel);
    private final StyledTable goalTable = table(goalModel);
    private final StyledTable debtTable = table(debtModel);
    private final JLabel status = new JLabel("Ready");
    private final JTabbedPane tabs = new JTabbedPane();

    public PlanningPanel(
            AdvancedBudgetService budgetService,
            SavingsGoalService goalService,
            DebtService debtService,
            AccountService accountService,
            CategoryService categoryService) {
        requireEventDispatchThread();
        this.budgetService = Objects.requireNonNull(budgetService);
        this.goalService = Objects.requireNonNull(goalService);
        this.debtService = Objects.requireNonNull(debtService);
        this.accountService = Objects.requireNonNull(accountService);
        this.categoryService = Objects.requireNonNull(categoryService);
        buildInterface();
        refreshPlanning();
    }

    public void refreshPlanning() {
        requireEventDispatchThread();
        try {
            refreshBudgets();
            refreshGoals();
            refreshDebts();
            status.setText("Planning data is current · contributions and repayments "
                    + "are memo records and do not change account balances.");
        } catch (ValidationException | RepositoryException exception) {
            status.setText("Refresh failed: " + safeMessage(exception));
        }
    }

    public void showGoals() {
        tabs.setSelectedIndex(1);
        refreshPlanning();
    }

    public void showLoansAndDebts() {
        tabs.setSelectedIndex(2);
        refreshPlanning();
    }

    private void buildInterface() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        JLabel title = new JLabel("Financial Planning");
        title.setFont(AppFonts.pageTitle());
        JLabel subtitle = new JLabel(
                "Custom budgets, savings targets, and money owed or owing");
        AppTheme.mark(subtitle, AppTheme.SECONDARY_TEXT_ROLE);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 3));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(subtitle);
        add(heading, BorderLayout.NORTH);

        tabs.addTab("Budget Plans", section(budgetTable,
                button("New Budget", this::addBudget, true),
                button("Archive / Restore", this::toggleBudget, false),
                button("Refresh", this::refreshPlanning, false)));
        tabs.addTab("Savings Goals", section(goalTable,
                button("New Goal", this::addGoal, true),
                button("Add Contribution", this::addContribution, false),
                button("History", this::showGoalHistory, false),
                button("Archive / Restore", this::toggleGoal, false)));
        tabs.addTab("Loans & Debts", section(debtTable,
                button("New Record", this::addDebt, true),
                button("Add Repayment", this::addRepayment, false),
                button("History", this::showDebtHistory, false)));
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private JPanel section(JTable table, Component... actions) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        for (Component action : actions) bar.add(action);
        panel.add(bar, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private Component button(String label, Runnable action, boolean primary) {
        javax.swing.JButton button = primary
                ? new PrimaryButton(label) : new SecondaryButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void refreshBudgets() {
        List<Object[]> rows = new ArrayList<>();
        for (BudgetPlan plan : budgetService.listHistory()) {
            BudgetPlanStatus value = budgetService.evaluate(plan.getIdentifier());
            String limit = value.getOverallUsage().getLimit()
                    .map(BigDecimal::toPlainString)
                    .orElse(plan.getCategoryLimits().size() + " category limit(s)");
            String usage = value.getOverallUsage().getUsagePercentage()
                    .map(percent -> percent.toPlainString() + "%")
                    .orElse("Category-specific");
            rows.add(new Object[]{plan.getIdentifier(), plan.getName(),
                plan.getStartDate() + " — " + plan.getEndDate(), limit,
                value.getOverallUsage().getSpent().toPlainString(), usage,
                value.getHighestAlertLevel().name().replace('_', ' '),
                plan.getRolloverMode().name().replace('_', ' '),
                plan.isActive() ? "Active" : "Archived"});
        }
        budgetModel.replace(rows);
    }

    private void refreshGoals() {
        List<Object[]> rows = new ArrayList<>();
        for (SavingsGoal goal : goalService.listGoals()) {
            SavingsGoalProgress value = goalService.getProgress(
                    goal.getIdentifier());
            rows.add(new Object[]{goal.getIdentifier(), goal.getName(),
                goal.getLinkedAccount().getDisplayName(),
                goal.getTargetAmount().toPlainString(),
                value.contributedAmount().toPlainString(),
                value.progressPercentage().toPlainString() + "%",
                goal.getTargetDate(), goal.isActive()
                        ? (value.achieved() ? "Achieved" : "Active")
                        : "Archived"});
        }
        goalModel.replace(rows);
    }

    private void refreshDebts() {
        List<Object[]> rows = new ArrayList<>();
        for (DebtProgress value : debtService.listProgress(LocalDate.now())) {
            var debt = value.debt();
            rows.add(new Object[]{debt.getIdentifier(), debt.getDirection(),
                debt.getCounterparty(), debt.getOriginalAmount().toPlainString(),
                value.repaidAmount().toPlainString(),
                value.remainingAmount().toPlainString(), debt.getDueDate(),
                value.status().name().replace('_', ' ')});
        }
        debtModel.replace(rows);
    }

    private void addBudget() {
        JTextField name = new JTextField();
        JTextField start = new JTextField(YearMonth.now().atDay(1).toString());
        JTextField end = new JTextField(YearMonth.now().atEndOfMonth().toString());
        JTextField overall = new JTextField();
        JComboBox<Choice<Category>> category = new JComboBox<>();
        category.addItem(new Choice<>(null, "No category limit"));
        for (Category item : categoryService.listSelectableCategories()) {
            category.addItem(new Choice<>(item, item.getDisplayName()));
        }
        JTextField categoryLimit = new JTextField();
        JComboBox<BudgetRolloverMode> rollover =
                new JComboBox<>(BudgetRolloverMode.values());
        if (!confirm("New Budget Plan", form(
                "Name", name, "Start (yyyy-MM-dd)", start,
                "End (yyyy-MM-dd)", end, "Overall limit (optional)", overall,
                "Category (optional)", category,
                "Category limit", categoryLimit, "Rollover", rollover))) return;
        runAction("Budget created.", () -> {
            Map<Category, BigDecimal> limits = new LinkedHashMap<>();
            Choice<Category> selected = selected(category);
            if (selected.value() != null) {
                limits.put(selected.value(), amount(categoryLimit.getText()));
            } else if (!categoryLimit.getText().isBlank()) {
                throw new ValidationException(
                        "Select a category for the category limit.");
            }
            budgetService.addPlan(name.getText(), date(start), date(end),
                    optionalAmount(overall.getText()), limits,
                    (BudgetRolloverMode) rollover.getSelectedItem());
        });
    }

    private void toggleBudget() {
        String id = selectedId(budgetTable);
        if (id == null) return;
        BudgetPlan plan = budgetService.listHistory().stream()
                .filter(item -> item.getIdentifier().equals(id)).findFirst()
                .orElseThrow();
        runAction(plan.isActive() ? "Budget archived." : "Budget restored.",
                () -> budgetService.setActive(id, !plan.isActive()));
    }

    private void addGoal() {
        JTextField name = new JTextField();
        JTextField target = new JTextField();
        JTextField date = new JTextField(LocalDate.now().plusMonths(6).toString());
        JComboBox<Account> account = accountChoices();
        if (!confirm("New Savings Goal", form("Name", name,
                "Target amount", target, "Target date (yyyy-MM-dd)", date,
                "Linked account", account))) return;
        runAction("Savings goal created.", () -> goalService.addGoal(
                name.getText(), amount(target.getText()), date(date),
                (Account) account.getSelectedItem()));
    }

    private void addContribution() {
        String id = selectedId(goalTable);
        if (id == null) return;
        JTextField amount = new JTextField();
        JTextField date = new JTextField(LocalDate.now().toString());
        JTextField note = new JTextField();
        if (!confirm("Add Goal Contribution", form("Amount", amount,
                "Date (yyyy-MM-dd)", date, "Note", note))) return;
        runAction("Contribution recorded.", () -> goalService.addContribution(
                id, date(date), amount(amount.getText()), note.getText()));
    }

    private void toggleGoal() {
        String id = selectedId(goalTable);
        if (id == null) return;
        SavingsGoal goal = goalService.listGoals().stream()
                .filter(item -> item.getIdentifier().equals(id)).findFirst()
                .orElseThrow();
        runAction(goal.isActive() ? "Goal archived." : "Goal restored.",
                () -> goalService.setActive(id, !goal.isActive()));
    }

    private void showGoalHistory() {
        String id = selectedId(goalTable);
        if (id == null) return;
        SavingsGoalProgress progress = goalService.getProgress(id);
        StringBuilder text = new StringBuilder(progress.goal().getName())
                .append("\nContributed: ").append(progress.contributedAmount())
                .append(" / ").append(progress.goal().getTargetAmount());
        progress.contributions().forEach(item -> text.append("\n")
                .append(item.getDate()).append("  ").append(item.getAmount())
                .append("  ").append(item.getNote()));
        showHistory("Contribution History", text.toString());
    }

    private void addDebt() {
        JComboBox<DebtDirection> direction =
                new JComboBox<>(DebtDirection.values());
        JTextField counterparty = new JTextField();
        JTextField original = new JTextField();
        JTextField due = new JTextField(LocalDate.now().plusMonths(1).toString());
        JTextField note = new JTextField();
        if (!confirm("New Loan or Debt", form("Direction", direction,
                "Counterparty", counterparty, "Original amount", original,
                "Due date (yyyy-MM-dd)", due, "Note", note))) return;
        runAction("Debt record created.", () -> debtService.addDebt(
                (DebtDirection) direction.getSelectedItem(),
                counterparty.getText(), amount(original.getText()), date(due),
                note.getText()));
    }

    private void addRepayment() {
        String id = selectedId(debtTable);
        if (id == null) return;
        JTextField amount = new JTextField();
        JTextField date = new JTextField(LocalDate.now().toString());
        JTextField note = new JTextField();
        if (!confirm("Add Repayment", form("Amount", amount,
                "Date (yyyy-MM-dd)", date, "Note", note))) return;
        runAction("Repayment recorded.", () -> debtService.addRepayment(
                id, date(date), amount(amount.getText()), note.getText()));
    }

    private void showDebtHistory() {
        String id = selectedId(debtTable);
        if (id == null) return;
        DebtProgress progress = debtService.getProgress(id, LocalDate.now());
        StringBuilder text = new StringBuilder(
                progress.debt().getCounterparty())
                .append("\nRemaining: ").append(progress.remainingAmount())
                .append(" · ").append(progress.status());
        progress.repayments().forEach(item -> text.append("\n")
                .append(item.getDate()).append("  ").append(item.getAmount())
                .append("  ").append(item.getNote()));
        showHistory("Repayment History", text.toString());
    }

    private void runAction(String success, Runnable action) {
        try {
            action.run();
            refreshPlanning();
            status.setText(success);
        } catch (DateTimeParseException exception) {
            showError("Dates must use yyyy-MM-dd.");
        } catch (NumberFormatException exception) {
            showError("Amounts must be valid decimal numbers.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private JComboBox<Account> accountChoices() {
        JComboBox<Account> choices = new JComboBox<>();
        for (Account account : accountService.listSelectableAccounts()) {
            choices.addItem(account);
        }
        return choices;
    }

    private static JPanel form(Object... fields) {
        JPanel panel = new JPanel(new GridLayout(fields.length / 2, 2, 8, 8));
        for (int index = 0; index < fields.length; index += 2) {
            panel.add(new JLabel(fields[index].toString()));
            panel.add((Component) fields[index + 1]);
        }
        return panel;
    }

    private boolean confirm(String title, JPanel form) {
        return JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION;
    }

    private void showHistory(String title, String text) {
        JOptionPane.showMessageDialog(this, text, title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Planning Action Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private String selectedId(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            showError("Select a row first.");
            return null;
        }
        return table.getModel().getValueAt(
                table.convertRowIndexToModel(selected), 0).toString();
    }

    private static LocalDate date(JTextField field) {
        return LocalDate.parse(field.getText().strip());
    }
    private static BigDecimal amount(String value) {
        return new BigDecimal(value.strip());
    }
    private static BigDecimal optionalAmount(String value) {
        return value == null || value.isBlank() ? null : amount(value);
    }
    @SuppressWarnings("unchecked")
    private static <T> Choice<T> selected(JComboBox<Choice<T>> combo) {
        return (Choice<T>) combo.getSelectedItem();
    }
    private static StyledTable table(DataTableModel model) {
        StyledTable table = new StyledTable(model);
        table.removeColumn(table.getColumnModel().getColumn(0));
        return table;
    }
    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The action could not be completed safely."
                : exception.getMessage();
    }
    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException(
                "PlanningPanel must be used on the Event Dispatch Thread.");
    }

    private record Choice<T>(T value, String label) {
        @Override public String toString() { return label; }
    }

    private static final class DataTableModel extends AbstractTableModel {
        private final String[] columns;
        private List<Object[]> rows = List.of();
        DataTableModel(String[] columns) { this.columns = columns.clone(); }
        void replace(List<Object[]> values) {
            rows = values.stream().map(Object[]::clone).toList();
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int row, int column) {
            return rows.get(row)[column];
        }
        @Override public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
