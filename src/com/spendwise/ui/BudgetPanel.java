package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.BudgetAlertLevel;
import com.spendwise.service.BudgetService;
import com.spendwise.service.BudgetStatusSnapshot;
import com.spendwise.service.BudgetUsage;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseAnalyticsSnapshot;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

public final class BudgetPanel extends JPanel {

    private static final Color PAGE_BACKGROUND = new Color(244, 247, 250);
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color PRIMARY_COLOR = new Color(42, 92, 130);
    private static final Color SECONDARY_TEXT = new Color(80, 90, 100);
    private static final Color NEUTRAL_COLOR = new Color(85, 96, 106);
    private static final Color WITHIN_COLOR = new Color(61, 105, 118);
    private static final Color NEAR_COLOR = new Color(190, 132, 39);
    private static final Color REACHED_COLOR = new Color(190, 91, 42);
    private static final Color OVER_COLOR = new Color(174, 61, 61);

    private final ExpenseAnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final JComboBox<Month> monthComboBox = new JComboBox<>(Month.values());
    private final JSpinner yearSpinner;
    private final JTextField overallLimitField = new JTextField(16);
    private final BudgetLimitTableModel tableModel = new BudgetLimitTableModel();
    private final JTable categoryTable = new JTable(tableModel);
    private final JLabel spentValue = createStatusValue();
    private final JLabel limitValue = createStatusValue();
    private final JLabel remainingValue = createStatusValue();
    private final JLabel percentageValue = createStatusValue();
    private final JLabel warningValue = createStatusValue();
    private final JLabel statusLabel = new JLabel("Loading budget status...");

    private YearMonth displayedMonth;
    private boolean unsavedChanges;
    private boolean replacingEditorValues;

    public BudgetPanel(
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService) {
        this(analyticsService, budgetService, null, YearMonth.now());
    }

    public BudgetPanel(
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService,
            CategoryService categoryService) {
        this(analyticsService, budgetService, categoryService, YearMonth.now());
    }

    BudgetPanel(
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService,
            YearMonth initialMonth) {
        this(analyticsService, budgetService, null, initialMonth);
    }

    BudgetPanel(
            ExpenseAnalyticsService analyticsService,
            BudgetService budgetService,
            CategoryService categoryService,
            YearMonth initialMonth) {
        requireEventDispatchThread();
        this.analyticsService = Objects.requireNonNull(
                analyticsService, "Expense analytics service is required.");
        this.budgetService = Objects.requireNonNull(
                budgetService, "Budget service is required.");
        this.categoryService = categoryService;
        YearMonth requiredInitialMonth = Objects.requireNonNull(
                initialMonth, "Initial budget month is required.");

        yearSpinner = new JSpinner(new SpinnerNumberModel(
                requiredInitialMonth.getYear(), 1, 9999, 1));
        yearSpinner.setEditor(new JSpinner.NumberEditor(yearSpinner, "0"));
        monthComboBox.setSelectedItem(requiredInitialMonth.getMonth());

        configureControls();
        buildInterface();
        refreshStatus(false, null);
    }

    public void refreshBudgetStatus() {
        requireEventDispatchThread();
        if (!commitActiveCategoryEdit()) {
            return;
        }
        try {
            yearSpinner.commitEdit();
        } catch (java.text.ParseException exception) {
            showFailure("Enter a year from 1 through 9999.", true);
            return;
        }

        BudgetDraftState preservedState = captureDraftState();
        if (preservedState.unsavedChanges() && displayedMonth != null) {
            selectDisplayedMonth();
        }
        try {
            refreshStatus(
                    true,
                    preservedState.unsavedChanges() ? preservedState : null);
        } finally {
            restoreSelectedPeriod(preservedState);
        }
    }

    YearMonth getDisplayedMonth() {
        return displayedMonth;
    }

    int getSelectedYear() {
        return ((Number) yearSpinner.getValue()).intValue();
    }

    String getSpentText() {
        return spentValue.getText();
    }

    String getLimitText() {
        return limitValue.getText();
    }

    String getRemainingText() {
        return remainingValue.getText();
    }

    String getPercentageText() {
        return percentageValue.getText();
    }

    String getWarningText() {
        return warningValue.getText();
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    BudgetLimitTableModel getBudgetTableModel() {
        return tableModel;
    }

    void setOverallLimitText(String text) {
        overallLimitField.setText(text);
    }

    String getOverallLimitEditorText() {
        return overallLimitField.getText();
    }

    boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    YearMonth getSelectedPeriod() {
        return selectedYearMonth();
    }

    void setSelectedPeriod(YearMonth month) {
        YearMonth requiredMonth = Objects.requireNonNull(
                month, "Selected budget month is required.");
        monthComboBox.setSelectedItem(requiredMonth.getMonth());
        yearSpinner.setValue(requiredMonth.getYear());
    }

    JTable getCategoryTable() {
        return categoryTable;
    }

    static Optional<BigDecimal> parseOptionalLimit(
            String text, String fieldName) {
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(normalizedText));
        } catch (NumberFormatException exception) {
            throw new ValidationException(
                    fieldName + " must be a valid decimal number.");
        }
    }

    private void configureControls() {
        monthComboBox.setRenderer(new MonthRenderer());
        monthComboBox.getAccessibleContext().setAccessibleName("Budget month");
        yearSpinner.getAccessibleContext().setAccessibleName("Budget year");
        JFormattedTextField yearField =
                ((JSpinner.DefaultEditor) yearSpinner.getEditor()).getTextField();
        yearField.setColumns(5);

        overallLimitField.getDocument().addDocumentListener(
                new ChangeDocumentListener(this::markUnsavedChanges));
        tableModel.addTableModelListener(event -> markUnsavedChanges());
    }

    private void buildInterface() {
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        setBackground(PAGE_BACKGROUND);

        add(createHeaderArea(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);
        add(createActions(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderArea() {
        JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Monthly Budgets");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        title.setForeground(PRIMARY_COLOR);
        JLabel subtitle = new JLabel(
                "Set informational spending limits without blocking expense changes.");
        subtitle.setForeground(SECONDARY_TEXT);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);

        JPanel selector = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        selector.setOpaque(false);
        selector.add(new JLabel("Month"));
        selector.add(monthComboBox);
        selector.add(new JLabel("Year"));
        selector.add(yearSpinner);
        JButton loadButton = new JButton("Load Month");
        loadButton.addActionListener(event -> loadSelectedMonth());
        selector.add(loadButton);

        headerPanel.add(titlePanel, BorderLayout.CENTER);
        headerPanel.add(selector, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel createBody() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel editor = createOverallEditor();
        editor.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel cards = createStatusCards();
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane tableScroll = createCategoryTable();
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(editor);
        body.add(Box.createVerticalStrut(12));
        body.add(cards);
        body.add(Box.createVerticalStrut(12));
        body.add(tableScroll);
        return body;
    }

    private JPanel createOverallEditor() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD_BACKGROUND);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JLabel label = new JLabel("Overall monthly limit:");
        label.setLabelFor(overallLimitField);
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 5, 10);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = 0;
        fieldConstraints.anchor = GridBagConstraints.WEST;
        fieldConstraints.insets = new Insets(0, 0, 5, 0);
        panel.add(overallLimitField, fieldConstraints);

        JLabel note = new JLabel(
                "Blank means not configured. Category limits are independent "
                + "and do not need to equal the overall limit.");
        note.setForeground(SECONDARY_TEXT);
        GridBagConstraints noteConstraints = new GridBagConstraints();
        noteConstraints.gridx = 0;
        noteConstraints.gridy = 1;
        noteConstraints.gridwidth = 2;
        noteConstraints.weightx = 1.0;
        noteConstraints.fill = GridBagConstraints.HORIZONTAL;
        noteConstraints.anchor = GridBagConstraints.WEST;
        panel.add(note, noteConstraints);
        return panel;
    }

    private JPanel createStatusCards() {
        JPanel cards = new JPanel(new GridLayout(1, 5, 10, 0));
        cards.setOpaque(false);
        cards.add(createStatusCard("Monthly Spent", spentValue));
        cards.add(createStatusCard("Monthly Limit", limitValue));
        cards.add(createStatusCard("Remaining", remainingValue));
        cards.add(createStatusCard("Usage", percentageValue));
        cards.add(createStatusCard("Status", warningValue));
        return cards;
    }

    private JPanel createStatusCard(String title, JLabel value) {
        JPanel card = new JPanel();
        card.setBackground(CARD_BACKGROUND);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(11, 12, 11, 12)));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(SECONDARY_TEXT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(value);
        return card;
    }

    private JScrollPane createCategoryTable() {
        categoryTable.setFillsViewportHeight(true);
        categoryTable.setRowHeight(24);
        categoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryTable.getTableHeader().setReorderingAllowed(false);
        TableColumnModel columns = categoryTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(170);
        columns.getColumn(1).setPreferredWidth(110);
        columns.getColumn(2).setPreferredWidth(110);
        columns.getColumn(3).setPreferredWidth(110);
        columns.getColumn(4).setPreferredWidth(135);
        MoneyOrTextRenderer moneyRenderer = new MoneyOrTextRenderer();
        columns.getColumn(1).setCellRenderer(moneyRenderer);
        columns.getColumn(2).setCellRenderer(moneyRenderer);
        columns.getColumn(3).setCellRenderer(moneyRenderer);
        columns.getColumn(4).setCellRenderer(new AlertRenderer());

        JScrollPane scrollPane = new JScrollPane(categoryTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(210, 218, 225)),
                "Category Limits"));
        scrollPane.setPreferredSize(new Dimension(900, 270));
        return scrollPane;
    }

    private JPanel createActions() {
        JPanel actionArea = new JPanel(new BorderLayout(10, 0));
        actionArea.setOpaque(false);
        statusLabel.setForeground(SECONDARY_TEXT);

        JButton saveButton = new JButton("Save Budget");
        saveButton.addActionListener(event -> saveBudget());
        JButton clearButton = new JButton("Clear Budget");
        clearButton.addActionListener(event -> clearBudget());
        JButton refreshButton = new JButton("Refresh Status");
        refreshButton.addActionListener(event -> refreshBudgetStatus());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(refreshButton);
        buttons.add(clearButton);
        buttons.add(saveButton);

        actionArea.add(statusLabel, BorderLayout.CENTER);
        actionArea.add(buttons, BorderLayout.EAST);
        return actionArea;
    }

    private void loadSelectedMonth() {
        YearMonth selectedMonth;
        try {
            yearSpinner.commitEdit();
            selectedMonth = selectedYearMonth();
        } catch (java.text.ParseException | RuntimeException exception) {
            showFailure("Enter a year from 1 through 9999.", true);
            return;
        }

        if (unsavedChanges && !confirmDiscardEdits()) {
            selectDisplayedMonth();
            return;
        }
        refreshStatus(true, null);
    }

    private boolean confirmDiscardEdits() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Discard unsaved budget changes and load the selected month?",
                "Unsaved Budget Changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private void saveBudget() {
        try {
            yearSpinner.commitEdit();
            YearMonth month = selectedYearMonth();
            if (!month.equals(displayedMonth)) {
                throw new ValidationException(
                        "Load the selected month before saving its budget.");
            }
            if (categoryTable.isEditing()
                    && !categoryTable.getCellEditor().stopCellEditing()) {
                throw new ValidationException(
                        "Finish editing the category limit before saving.");
            }
            Optional<BigDecimal> overallLimit = parseOptionalLimit(
                    overallLimitField.getText(), "Overall limit");
            LinkedHashMap<Category, BigDecimal> categoryLimits =
                    new LinkedHashMap<>();
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                Category category = tableModel.getCategoryAt(row);
                parseOptionalLimit(
                        tableModel.getLimitTextAt(row),
                        category.getDisplayName() + " limit")
                        .ifPresent(limit -> categoryLimits.put(category, limit));
            }

            budgetService.saveBudget(
                    new MonthlyBudget(month, overallLimit, categoryLimits));
            refreshStatus(false, null);
            statusLabel.setText("Budget saved for " + month + ".");
        } catch (java.text.ParseException exception) {
            showFailure("Enter a year from 1 through 9999.", true);
        } catch (ValidationException | RepositoryException exception) {
            showFailure(safeMessage(exception), true);
        }
    }

    private void clearBudget() {
        YearMonth month = displayedMonth;
        if (month == null) {
            showFailure("Load a budget month before clearing.", true);
            return;
        }
        try {
            yearSpinner.commitEdit();
            if (!month.equals(selectedYearMonth())) {
                showFailure(
                        "Load the selected month before clearing its budget.",
                        true);
                return;
            }
        } catch (java.text.ParseException | RuntimeException exception) {
            showFailure("Enter a year from 1 through 9999.", true);
            return;
        }
        if (!GraphicsEnvironment.isHeadless()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Clear all configured limits for " + month + "?",
                    "Confirm Budget Clear",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        } else {
            return;
        }

        try {
            boolean cleared = budgetService.clearBudget(month);
            refreshStatus(false, null);
            statusLabel.setText(cleared
                    ? "Budget cleared for " + month + "."
                    : "No budget was configured for " + month + ".");
        } catch (RepositoryException exception) {
            showFailure(safeMessage(exception), true);
        }
    }

    private void refreshStatus(
            boolean showFailureDialog, BudgetDraftState preservedState) {
        YearMonth selectedMonth;
        try {
            yearSpinner.commitEdit();
            selectedMonth = selectedYearMonth();
        } catch (java.text.ParseException | RuntimeException exception) {
            showFailure("Enter a year from 1 through 9999.", showFailureDialog);
            return;
        }

        try {
            ExpenseAnalyticsSnapshot analyticsSnapshot =
                    analyticsService.analyzeMonth(selectedMonth);
            BudgetStatusSnapshot budgetSnapshot =
                    budgetService.evaluate(analyticsSnapshot);
            List<Category> selectableCategorySnapshot = selectableCategories();
            applyStatus(budgetSnapshot, selectableCategorySnapshot);
            if (preservedState != null) {
                replacingEditorValues = true;
                try {
                    overallLimitField.setText(preservedState.overallLimit());
                    tableModel.restoreLimitValuesByIdentifier(
                            preservedState.categoryLimitsByIdentifier());
                } finally {
                    replacingEditorValues = false;
                }
                unsavedChanges = preservedState.unsavedChanges();
                statusLabel.setText(
                        selectedMonth + " status refreshed; unsaved changes remain.");
            }
        } catch (ValidationException | RepositoryException exception) {
            selectDisplayedMonth();
            showFailure(safeMessage(exception), showFailureDialog);
        }
    }

    private boolean commitActiveCategoryEdit() {
        if (!categoryTable.isEditing()) {
            return true;
        }
        try {
            if (categoryTable.getCellEditor() != null
                    && categoryTable.getCellEditor().stopCellEditing()) {
                return true;
            }
        } catch (RuntimeException exception) {
            showFailure(
                    "Finish editing the category limit before refreshing.",
                    true);
            return false;
        }
        showFailure(
                "Finish editing the category limit before refreshing.",
                true);
        return false;
    }

    private BudgetDraftState captureDraftState() {
        return new BudgetDraftState(
                overallLimitField.getText(),
                tableModel.copyLimitValuesByIdentifier(),
                (Month) monthComboBox.getSelectedItem(),
                ((Number) yearSpinner.getValue()).intValue(),
                unsavedChanges);
    }

    private void restoreSelectedPeriod(BudgetDraftState state) {
        if (state.selectedMonth() != null) {
            monthComboBox.setSelectedItem(state.selectedMonth());
        }
        yearSpinner.setValue(state.selectedYear());
    }

    private void applyStatus(
            BudgetStatusSnapshot snapshot,
            List<Category> selectableCategorySnapshot) {
        BudgetPanelViewData viewData = BudgetPanelViewData.from(snapshot);
        replacingEditorValues = true;
        try {
            displayedMonth = snapshot.getSelectedMonth();
            selectDisplayedMonth();
            overallLimitField.setText(
                    snapshot.getOverallUsage()
                            .getLimit()
                            .map(BigDecimal::toPlainString)
                            .orElse(""));
            tableModel.replaceStatus(snapshot, selectableCategorySnapshot);
        } finally {
            replacingEditorValues = false;
        }

        spentValue.setText(viewData.spentText());
        limitValue.setText(viewData.limitText());
        remainingValue.setText(viewData.remainingText());
        percentageValue.setText(viewData.percentageText());
        warningValue.setText(viewData.warningText());
        Color warningColor = alertColor(
                snapshot.getOverallUsage().getAlertLevel());
        remainingValue.setForeground(warningColor);
        percentageValue.setForeground(warningColor);
        warningValue.setForeground(warningColor);
        unsavedChanges = false;
        statusLabel.setText(displayedMonth + " budget status refreshed.");
    }

    private List<Category> selectableCategories() {
        return categoryService == null
                ? List.of(Category.values())
                : categoryService.listSelectableCategories();
    }

    private YearMonth selectedYearMonth() {
        Month month = (Month) monthComboBox.getSelectedItem();
        if (month == null) {
            throw new ValidationException("Budget month is required.");
        }
        return YearMonth.of(
                ((Number) yearSpinner.getValue()).intValue(),
                month);
    }

    private void selectDisplayedMonth() {
        if (displayedMonth != null) {
            monthComboBox.setSelectedItem(displayedMonth.getMonth());
            yearSpinner.setValue(displayedMonth.getYear());
        }
    }

    private void markUnsavedChanges() {
        if (replacingEditorValues || displayedMonth == null) {
            return;
        }
        unsavedChanges = true;
        statusLabel.setText("Budget has unsaved changes.");
    }

    private void showFailure(String message, boolean showDialog) {
        String safeMessage = message == null || message.isBlank()
                ? "Budget status could not be updated safely."
                : message;
        statusLabel.setText("Budget operation failed: " + safeMessage);
        if (showDialog && !GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(
                    this,
                    safeMessage,
                    "Budget Operation Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The budget operation could not be completed safely."
                : message;
    }

    private static Color alertColor(BudgetAlertLevel alertLevel) {
        return switch (alertLevel) {
            case NOT_SET -> NEUTRAL_COLOR;
            case WITHIN_LIMIT -> WITHIN_COLOR;
            case NEAR_LIMIT -> NEAR_COLOR;
            case LIMIT_REACHED -> REACHED_COLOR;
            case OVER_LIMIT -> OVER_COLOR;
        };
    }

    private static String alertText(BudgetAlertLevel alertLevel) {
        return switch (alertLevel) {
            case NOT_SET -> "Not set";
            case WITHIN_LIMIT -> "Within limit";
            case NEAR_LIMIT -> "Near limit";
            case LIMIT_REACHED -> "Limit reached";
            case OVER_LIMIT -> "Over limit";
        };
    }

    private static JLabel createStatusValue() {
        JLabel label = new JLabel("Not set");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setForeground(PRIMARY_COLOR);
        return label;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "BudgetPanel must be created and updated on the Event Dispatch Thread.");
        }
    }

    private record BudgetDraftState(
            String overallLimit,
            Map<String, Object> categoryLimitsByIdentifier,
            Month selectedMonth,
            int selectedYear,
            boolean unsavedChanges) {
    }

    private record BudgetPanelViewData(
            String spentText,
            String limitText,
            String remainingText,
            String percentageText,
            String warningText) {

        static BudgetPanelViewData from(BudgetStatusSnapshot snapshot) {
            BudgetUsage usage = snapshot.getOverallUsage();
            return new BudgetPanelViewData(
                    usage.getSpent().toPlainString(),
                    usage.getLimit().map(BigDecimal::toPlainString).orElse("Not set"),
                    usage.getRemaining()
                            .map(BigDecimal::toPlainString)
                            .orElse("Not set"),
                    usage.getUsagePercentage()
                            .map(value -> value.toPlainString() + "%")
                            .orElse("Not set"),
                    alertText(usage.getAlertLevel()));
        }
    }

    private static final class ChangeDocumentListener
            implements DocumentListener {

        private final Runnable changeAction;

        ChangeDocumentListener(Runnable changeAction) {
            this.changeAction = changeAction;
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            changeAction.run();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            changeAction.run();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            changeAction.run();
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

    private static class MoneyOrTextRenderer extends DefaultTableCellRenderer {

        MoneyOrTextRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            setText(value instanceof BigDecimal amount
                    ? amount.toPlainString()
                    : Objects.toString(value, ""));
        }
    }

    private static final class AlertRenderer extends DefaultTableCellRenderer {

        @Override
        protected void setValue(Object value) {
            if (value instanceof BudgetAlertLevel alertLevel) {
                setText("  " + alertText(alertLevel));
                setForeground(alertColor(alertLevel));
            } else {
                setText("");
                setForeground(NEUTRAL_COLOR);
            }
        }
    }
}
