package com.spendwise.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.ExpenseSortOrder;
import com.spendwise.service.ExpenseSummary;
import com.spendwise.service.AccountService;
import com.spendwise.service.CategoryService;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

public final class ExpensePanel extends JPanel {

    private static final Color PAGE_BACKGROUND = new Color(244, 247, 250);
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color PRIMARY_COLOR = new Color(42, 92, 130);
    private static final Color SECONDARY_TEXT = new Color(80, 90, 100);

    private final ExpenseService expenseService;
    private final CategoryService categoryService;
    private final AccountService accountService;
    private final Predicate<Category> categoryReferenceChecker;
    private final Runnable categoryChangeListener;
    private final Runnable expenseChangeListener;
    private final ExpenseTableModel tableModel = new ExpenseTableModel();
    private final JTable expenseTable = new JTable(tableModel);
    private final JTextField searchField = new JTextField(20);
    private final JTextField startDateField = new JTextField(10);
    private final JTextField endDateField = new JTextField(10);
    private final JComboBox<CategoryChoice> categoryComboBox = new JComboBox<>();
    private final JComboBox<SortChoice> sortComboBox = new JComboBox<>();
    private final JLabel expenseCountValue = createSummaryValueLabel();
    private final JLabel totalAmountValue = createSummaryValueLabel();
    private final JLabel averageAmountValue = createSummaryValueLabel();
    private final JLabel statusLabel = new JLabel("Loading expenses...");

    public ExpensePanel(ExpenseService expenseService) {
        this(
                expenseService,
                null,
                null,
                category -> false,
                () -> {
                },
                () -> {
                });
    }

    public ExpensePanel(
            ExpenseService expenseService,
            CategoryService categoryService,
            Predicate<Category> categoryReferenceChecker,
            Runnable categoryChangeListener) {
        this(
                expenseService,
                categoryService,
                null,
                categoryReferenceChecker,
                categoryChangeListener,
                () -> {
                });
    }

    public ExpensePanel(
            ExpenseService expenseService,
            CategoryService categoryService,
            AccountService accountService,
            Predicate<Category> categoryReferenceChecker,
            Runnable categoryChangeListener,
            Runnable expenseChangeListener) {
        requireEventDispatchThread();
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.categoryService = categoryService;
        this.accountService = accountService;
        this.categoryReferenceChecker = Objects.requireNonNull(
                categoryReferenceChecker, "Category reference checker is required.");
        this.categoryChangeListener = Objects.requireNonNull(
                categoryChangeListener, "Category change listener is required.");
        this.expenseChangeListener = Objects.requireNonNull(
                expenseChangeListener, "Expense change listener is required.");

        populateFilterChoices();
        buildInterface();
        loadCurrentView("Expenses loaded.", true);
    }

    String getDisplayedExpenseCountText() {
        return expenseCountValue.getText();
    }

    String getDisplayedTotalAmountText() {
        return totalAmountValue.getText();
    }

    String getDisplayedAverageAmountText() {
        return averageAmountValue.getText();
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    int getCategoryFilterChoiceCount() {
        return categoryComboBox.getItemCount();
    }

    Category getCategoryFilterChoiceAt(int index) {
        return categoryComboBox.getItemAt(index).category();
    }

    List<Category> getSelectableCategorySnapshot(Expense expenseToEdit) {
        return selectableCategories(expenseToEdit);
    }

    List<Account> getSelectableAccountSnapshot(Expense expenseToEdit) {
        return selectableAccounts(expenseToEdit);
    }

    public void refreshCategoryChoices() {
        requireEventDispatchThread();
        String selectedIdentifier = selectedCategory() == null
                ? null
                : selectedCategory().getIdentifier();
        populateFilterChoices();
        if (selectedIdentifier != null) {
            selectCategoryFilter(selectedIdentifier);
        }
        loadCurrentView("Categories refreshed.", false);
    }

    public void refreshExpenses() {
        requireEventDispatchThread();
        loadCurrentView("Expenses refreshed.", false);
    }

    private void populateFilterChoices() {
        categoryComboBox.removeAllItems();
        categoryComboBox.addItem(new CategoryChoice(null, "All Categories"));
        for (Category category : availableCategories()) {
            categoryComboBox.addItem(
                    new CategoryChoice(category, category.getDisplayName()));
        }

        if (sortComboBox.getItemCount() == 0) {
            sortComboBox.addItem(
                    new SortChoice(ExpenseSortOrder.ORIGINAL_ORDER, "Original Order"));
            sortComboBox.addItem(
                    new SortChoice(
                            ExpenseSortOrder.DATE_NEWEST_FIRST,
                            "Date: Newest First"));
            sortComboBox.addItem(
                    new SortChoice(
                            ExpenseSortOrder.DATE_OLDEST_FIRST,
                            "Date: Oldest First"));
            sortComboBox.addItem(
                    new SortChoice(
                            ExpenseSortOrder.AMOUNT_HIGHEST_FIRST,
                            "Amount: Highest First"));
            sortComboBox.addItem(
                    new SortChoice(
                            ExpenseSortOrder.AMOUNT_LOWEST_FIRST,
                            "Amount: Lowest First"));
            sortComboBox.addItem(
                    new SortChoice(
                            ExpenseSortOrder.DESCRIPTION_A_TO_Z,
                            "Description: A to Z"));
            sortComboBox.addItem(
                    new SortChoice(
                            ExpenseSortOrder.DESCRIPTION_Z_TO_A,
                            "Description: Z to A"));
        }
    }

    private void buildInterface() {
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        setBackground(PAGE_BACKGROUND);

        JPanel topArea = new JPanel();
        topArea.setOpaque(false);
        topArea.setLayout(new BoxLayout(topArea, BoxLayout.Y_AXIS));
        topArea.add(createHeaderArea());
        topArea.add(Box.createVerticalStrut(14));
        topArea.add(createSummaryArea());
        topArea.add(Box.createVerticalStrut(14));
        topArea.add(createFilterArea());

        add(topArea, BorderLayout.NORTH);
        add(createTableArea(), BorderLayout.CENTER);
        add(createBottomArea(), BorderLayout.SOUTH);

        searchField.addActionListener(event -> loadCurrentView("Filters applied.", true));
        startDateField.addActionListener(event -> loadCurrentView("Filters applied.", true));
        endDateField.addActionListener(event -> loadCurrentView("Filters applied.", true));
    }

    private JPanel createHeaderArea() {
        JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(AppBrand.APP_NAME);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 25f));
        titleLabel.setForeground(PRIMARY_COLOR);

        JLabel subtitleLabel = new JLabel(
                "Add, review, and organize expenses from one focused workspace.");
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

        JButton addButton = new JButton("Add Expense");
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD));
        addButton.getAccessibleContext().setAccessibleDescription(
                "Open the form for a new expense");
        addButton.addActionListener(event -> addExpense());

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        if (categoryService != null) {
            JButton manageCategoriesButton = new JButton("Manage Categories");
            manageCategoriesButton.addActionListener(
                    event -> manageCategories());
            headerActions.add(manageCategoriesButton);
        }
        headerActions.add(addButton);

        headerPanel.add(titlePanel, BorderLayout.CENTER);
        headerPanel.add(headerActions, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel createSummaryArea() {
        JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        summaryPanel.setOpaque(false);
        summaryPanel.add(createSummaryCard("Total Expenses", expenseCountValue));
        summaryPanel.add(createSummaryCard("Total Amount", totalAmountValue));
        summaryPanel.add(createSummaryCard("Average Amount", averageAmountValue));
        return summaryPanel;
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

    private JPanel createFilterArea() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBackground(CARD_BACKGROUND);
        filterPanel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 231)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        int column = 0;
        column = addFilterField(filterPanel, column, "Search", searchField, 1.0);
        column = addFilterField(
                filterPanel, column, "Category", categoryComboBox, 0.0);
        column = addFilterField(
                filterPanel, column, "Start Date (yyyy-MM-dd)", startDateField, 0.0);
        column = addFilterField(
                filterPanel, column, "End Date (yyyy-MM-dd)", endDateField, 0.0);
        column = addFilterField(filterPanel, column, "Sort", sortComboBox, 0.0);

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(event -> loadCurrentView("Filters applied.", true));
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(event -> resetFilters());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> loadCurrentView("Refreshed.", true));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(applyButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(refreshButton);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = column;
        buttonConstraints.gridy = 1;
        buttonConstraints.anchor = GridBagConstraints.SOUTHEAST;
        buttonConstraints.insets = new Insets(4, 8, 0, 0);
        filterPanel.add(buttonPanel, buttonConstraints);
        return filterPanel;
    }

    private int addFilterField(
            JPanel panel,
            int column,
            String labelText,
            Component component,
            double horizontalWeight) {
        JLabel label = new JLabel(labelText);
        label.setLabelFor(component);
        label.setForeground(SECONDARY_TEXT);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = column;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 3, 8);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = column;
        fieldConstraints.gridy = 1;
        fieldConstraints.weightx = horizontalWeight;
        fieldConstraints.fill = horizontalWeight > 0
                ? GridBagConstraints.HORIZONTAL
                : GridBagConstraints.NONE;
        fieldConstraints.anchor = GridBagConstraints.WEST;
        fieldConstraints.insets = new Insets(0, 0, 0, 8);
        panel.add(component, fieldConstraints);
        return column + 1;
    }

    private JScrollPane createTableArea() {
        expenseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        expenseTable.setAutoCreateRowSorter(false);
        expenseTable.setFillsViewportHeight(true);
        expenseTable.setRowHeight(24);
        expenseTable.setShowVerticalLines(false);
        expenseTable.getTableHeader().setReorderingAllowed(false);

        TableColumnModel columns = expenseTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(95);
        columns.getColumn(1).setPreferredWidth(220);
        columns.getColumn(2).setPreferredWidth(120);
        columns.getColumn(3).setPreferredWidth(115);
        columns.getColumn(4).setPreferredWidth(360);
        columns.getColumn(3).setCellRenderer(new AmountCellRenderer());
        columns.getColumn(4).setCellRenderer(new NotesCellRenderer());

        JScrollPane scrollPane = new JScrollPane(expenseTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(210, 218, 225)));
        scrollPane.setPreferredSize(new Dimension(900, 330));
        return scrollPane;
    }

    private JPanel createBottomArea() {
        JPanel bottomPanel = new JPanel(new BorderLayout(12, 0));
        bottomPanel.setOpaque(false);

        statusLabel.setForeground(SECONDARY_TEXT);
        statusLabel.getAccessibleContext().setAccessibleDescription(
                "Expense list status");

        JButton editButton = new JButton("Edit Selected");
        editButton.addActionListener(event -> editSelectedExpense());
        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(event -> deleteSelectedExpense());

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsPanel.setOpaque(false);
        actionsPanel.add(editButton);
        actionsPanel.add(deleteButton);

        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(actionsPanel, BorderLayout.EAST);
        return bottomPanel;
    }

    private void addExpense() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        ExpenseFormDialog dialog;
        try {
            dialog = new ExpenseFormDialog(
                    owner,
                    expenseService,
                    null,
                    selectableCategories(null),
                    selectableAccounts(null),
                    preferredAccount());
        } catch (ValidationException | RepositoryException exception) {
            showLoadError("Unable to prepare a new expense.", exception);
            return;
        }
        if (dialog.showDialog()) {
            refreshAfterExpenseMutation("Expense added.");
        }
    }

    private void editSelectedExpense() {
        Expense selectedExpense = getSelectedExpense();
        if (selectedExpense == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select an expense to edit.",
                    "No Expense Selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        ExpenseFormDialog dialog;
        try {
            dialog = new ExpenseFormDialog(
                    owner,
                    expenseService,
                    selectedExpense,
                    selectableCategories(selectedExpense),
                    selectableAccounts(selectedExpense));
        } catch (ValidationException | RepositoryException exception) {
            showLoadError("Unable to prepare the selected expense.", exception);
            return;
        }
        if (dialog.showDialog()) {
            refreshAfterExpenseMutation("Expense updated.");
        }
    }

    private void manageCategories() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CategoryManagerDialog dialog = new CategoryManagerDialog(
                owner,
                categoryService,
                categoryReferenceChecker,
                () -> {
                    refreshCategoryChoices();
                    categoryChangeListener.run();
                });
        dialog.showDialog();
    }

    private void deleteSelectedExpense() {
        Expense selectedExpense = getSelectedExpense();
        if (selectedExpense == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select an expense to delete.",
                    "No Expense Selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete \"" + selectedExpense.getDescription() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean deleted = expenseService.deleteExpense(selectedExpense.getId());
            if (!deleted) {
                JOptionPane.showMessageDialog(
                        this,
                        "That expense no longer exists. The list will be refreshed.",
                        "Expense Not Found",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            if (deleted) {
                refreshAfterExpenseMutation("Expense deleted.");
            } else {
                loadCurrentView("Expense list refreshed.", true);
            }
        } catch (ValidationException | RepositoryException exception) {
            showLoadError("Unable to delete the expense.", exception);
        }
    }

    void refreshAfterExpenseMutation(String successMessage) {
        loadCurrentView(successMessage, true);
        try {
            expenseChangeListener.run();
        } catch (RuntimeException exception) {
            statusLabel.setText(
                    successMessage
                    + " One or more related views could not refresh; "
                    + "use the tab to retry.");
        }
    }

    private void resetFilters() {
        searchField.setText("");
        categoryComboBox.setSelectedIndex(0);
        startDateField.setText("");
        endDateField.setText("");
        sortComboBox.setSelectedIndex(0);
        loadCurrentView("Filters reset.", true);
    }

    private void loadCurrentView(String successMessage, boolean showFailureDialog) {
        String selectedExpenseId = selectedExpenseId();
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = parseOptionalDate(startDateField.getText(), "Start date");
            endDate = parseOptionalDate(endDateField.getText(), "End date");
        } catch (DateTimeParseException exception) {
            statusLabel.setText(exception.getMessage());
            if (showFailureDialog) {
                JOptionPane.showMessageDialog(
                        this,
                        exception.getMessage(),
                        "Invalid Date",
                        JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        try {
            List<Expense> displayedExpenses = expenseService.findExpenses(
                    searchField.getText(),
                    selectedCategory(),
                    startDate,
                    endDate,
                    selectedSortOrder());
            ExpenseSummary summary =
                    expenseService.calculateSummary(displayedExpenses);

            tableModel.replaceExpenses(displayedExpenses);
            updateSummary(summary);
            restoreSelection(selectedExpenseId);
            statusLabel.setText(
                    successMessage + " " + displayedExpenses.size()
                    + (displayedExpenses.size() == 1
                            ? " record displayed."
                            : " records displayed."));
        } catch (ValidationException | RepositoryException exception) {
            statusLabel.setText("Unable to load expenses: " + safeMessage(exception));
            if (showFailureDialog) {
                JOptionPane.showMessageDialog(
                        this,
                        safeMessage(exception),
                        "Unable to Load Expenses",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateSummary(ExpenseSummary summary) {
        expenseCountValue.setText(Integer.toString(summary.getExpenseCount()));
        totalAmountValue.setText(summary.getTotalAmount().toPlainString());
        averageAmountValue.setText(summary.getAverageAmount().toPlainString());
    }

    private Expense getSelectedExpense() {
        int selectedViewRow = expenseTable.getSelectedRow();
        if (selectedViewRow < 0) {
            return null;
        }
        int selectedModelRow = expenseTable.convertRowIndexToModel(selectedViewRow);
        return tableModel.getExpenseAt(selectedModelRow);
    }

    private String selectedExpenseId() {
        Expense selectedExpense = getSelectedExpense();
        return selectedExpense == null ? null : selectedExpense.getId();
    }

    private void restoreSelection(String expenseId) {
        if (expenseId == null) {
            return;
        }
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (tableModel.getExpenseAt(row).getId().equals(expenseId)) {
                int viewRow = expenseTable.convertRowIndexToView(row);
                expenseTable.setRowSelectionInterval(viewRow, viewRow);
                expenseTable.scrollRectToVisible(expenseTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private Category selectedCategory() {
        CategoryChoice selectedChoice =
                (CategoryChoice) categoryComboBox.getSelectedItem();
        return selectedChoice == null ? null : selectedChoice.category();
    }

    private List<Category> availableCategories() {
        return categoryService == null
                ? List.of(Category.values())
                : categoryService.listAllCategories();
    }

    private List<Category> selectableCategories(Expense expenseToEdit) {
        List<Category> categories = new ArrayList<>(categoryService == null
                ? List.of(Category.values())
                : categoryService.listSelectableCategories());
        if (expenseToEdit != null
                && !categories.contains(expenseToEdit.getCategory())) {
            categories.add(expenseToEdit.getCategory());
        }
        return List.copyOf(categories);
    }

    private List<Account> selectableAccounts(Expense expenseToEdit) {
        List<Account> accounts = new ArrayList<>(accountService == null
                ? List.of(Account.DEFAULT)
                : accountService.listSelectableAccounts());
        if (expenseToEdit != null
                && !accounts.contains(expenseToEdit.getAccount())) {
            accounts.add(expenseToEdit.getAccount());
        }
        return List.copyOf(accounts);
    }

    private Account preferredAccount() {
        return accountService == null
                ? Account.DEFAULT
                : accountService.getDefaultAccount();
    }

    private void selectCategoryFilter(String identifier) {
        for (int index = 1; index < categoryComboBox.getItemCount(); index++) {
            CategoryChoice choice = categoryComboBox.getItemAt(index);
            if (choice.category().getIdentifier().equals(identifier)) {
                categoryComboBox.setSelectedIndex(index);
                return;
            }
        }
        categoryComboBox.setSelectedIndex(0);
    }

    private ExpenseSortOrder selectedSortOrder() {
        SortChoice selectedChoice = (SortChoice) sortComboBox.getSelectedItem();
        return selectedChoice == null
                ? ExpenseSortOrder.ORIGINAL_ORDER
                : selectedChoice.sortOrder();
    }

    private static LocalDate parseOptionalDate(String text, String fieldName) {
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(normalizedText);
        } catch (DateTimeParseException exception) {
            throw new DateTimeParseException(
                    fieldName + " must use yyyy-MM-dd.",
                    normalizedText,
                    exception.getErrorIndex(),
                    exception);
        }
    }

    private void showLoadError(String context, RuntimeException exception) {
        String message = context + " " + safeMessage(exception);
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(
                this,
                message,
                "Expense Operation Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The operation could not be completed safely."
                : message;
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
                    "ExpensePanel must be created on the Event Dispatch Thread.");
        }
    }

    private record CategoryChoice(Category category, String label) {

        @Override
        public String toString() {
            return label;
        }
    }

    private record SortChoice(ExpenseSortOrder sortOrder, String label) {

        @Override
        public String toString() {
            return label;
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

    private static final class NotesCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            setToolTipText(value == null || value.toString().isBlank()
                    ? null
                    : value.toString());
            return component;
        }
    }
}
