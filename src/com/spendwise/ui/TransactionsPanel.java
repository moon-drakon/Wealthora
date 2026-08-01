package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import com.spendwise.service.AccountService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.TransferService;
import com.spendwise.ui.component.ConfirmationDialogs;
import com.spendwise.ui.component.EmptyStatePanel;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SearchField;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Font;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

public final class TransactionsPanel extends JPanel {

    private static final String ALL = "All";

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final Runnable dataChangedListener;
    private final TransactionTableModel tableModel = new TransactionTableModel();
    private final StyledTable table = new StyledTable(tableModel);
    private final SearchField searchField =
            new SearchField("Search descriptions, accounts, or categories", 25);
    private final JComboBox<Object> accountFilter = new JComboBox<>();
    private final JComboBox<Object> categoryFilter = new JComboBox<>();
    private final JComboBox<Object> typeFilter = new JComboBox<>(
            new Object[] {ALL, TransactionRow.Type.INCOME,
                TransactionRow.Type.EXPENSE, TransactionRow.Type.TRANSFER});
    private final JTextField startDateField = new JTextField(10);
    private final JTextField endDateField = new JTextField(10);
    private final JComboBox<SortOrder> sortOrder =
            new JComboBox<>(SortOrder.values());
    private final JButton editButton = new SecondaryButton("Edit selected");
    private final JButton deleteButton = new SecondaryButton("Delete selected");
    private final JLabel statusLabel = new JLabel(" ");
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(contentLayout);

    public TransactionsPanel(
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            AccountService accountService,
            CategoryService categoryService,
            Runnable dataChangedListener) {
        super(new BorderLayout(0, 14));
        this.expenseService = Objects.requireNonNull(expenseService);
        this.incomeService = Objects.requireNonNull(incomeService);
        this.transferService = Objects.requireNonNull(transferService);
        this.accountService = Objects.requireNonNull(accountService);
        this.categoryService = Objects.requireNonNull(categoryService);
        this.dataChangedListener = dataChangedListener == null
                ? () -> { } : dataChangedListener;
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        buildInterface();
        refreshTransactions();
    }

    public void refreshTransactions() {
        Object selectedAccount = accountFilter.getSelectedItem();
        Object selectedCategory = categoryFilter.getSelectedItem();
        populateFilters(selectedAccount, selectedCategory);
        applyFilters();
    }

    public void setSearchText(String query) {
        searchField.setText(query == null ? "" : query);
        searchField.requestFocusInWindow();
        searchField.selectAll();
        applyFilters();
    }

    public int getVisibleTransactionCount() {
        return tableModel.getRowCount();
    }

    private void buildInterface() {
        JPanel commandArea = new JPanel(new BorderLayout(12, 10));
        commandArea.setOpaque(false);
        commandArea.add(buildActionRow(), BorderLayout.NORTH);
        commandArea.add(buildFilterRow(), BorderLayout.CENTER);
        add(commandArea, BorderLayout.NORTH);

        table.setDefaultRenderer(TransactionRow.Type.class,
                new TypeRenderer());
        table.setDefaultRenderer(BigDecimal.class, new AmountRenderer());
        TableRowSorter<TransactionTableModel> rowSorter =
                new TableRowSorter<>(tableModel);
        rowSorter.setComparator(5, Comparator.naturalOrder());
        table.setRowSorter(rowSorter);
        table.getSelectionModel().addListSelectionListener(event ->
                updateSelectionActions());
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(190);
        table.getColumnModel().getColumn(5).setPreferredWidth(105);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColors.border()));
        contentPanel.add(scrollPane, "table");
        contentPanel.add(new EmptyStatePanel(
                "No transactions found",
                "Add a transaction or adjust the filters to see activity."),
                "empty");
        add(contentPanel, BorderLayout.CENTER);

        statusLabel.setFont(AppFonts.caption());
        AppTheme.mark(statusLabel, AppTheme.SECONDARY_TEXT_ROLE);
        add(statusLabel, BorderLayout.SOUTH);
        updateSelectionActions();
    }

    private JPanel buildActionRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JPanel addActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addActions.setOpaque(false);
        PrimaryButton addExpense = new PrimaryButton("Add Expense");
        addExpense.setMnemonic('E');
        addExpense.addActionListener(event -> openExpense(null));
        SecondaryButton addIncome = new SecondaryButton("Add Income");
        addIncome.setMnemonic('I');
        addIncome.addActionListener(event -> openIncome(null));
        SecondaryButton addTransfer = new SecondaryButton("Transfer");
        addTransfer.setMnemonic('T');
        addTransfer.addActionListener(event -> openTransfer(null));
        addActions.add(addExpense);
        addActions.add(addIncome);
        addActions.add(addTransfer);

        JPanel selectedActions = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0));
        selectedActions.setOpaque(false);
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        selectedActions.add(editButton);
        selectedActions.add(deleteButton);
        row.add(addActions, BorderLayout.WEST);
        row.add(selectedActions, BorderLayout.EAST);
        return row;
    }

    private JPanel buildFilterRow() {
        JPanel filters = new JPanel(new GridLayout(2, 1, 0, 8));
        AppTheme.mark(filters, AppTheme.CARD_ROLE);
        filters.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel primaryRow = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0));
        primaryRow.setOpaque(false);
        primaryRow.add(searchField);
        addLabeled(primaryRow, "Account", accountFilter);
        addLabeled(primaryRow, "Category", categoryFilter);
        addLabeled(primaryRow, "Type", typeFilter);
        JPanel secondaryRow = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0));
        secondaryRow.setOpaque(false);
        startDateField.setToolTipText("Start date (yyyy-MM-dd)");
        endDateField.setToolTipText("End date (yyyy-MM-dd)");
        addLabeled(secondaryRow, "From", startDateField);
        addLabeled(secondaryRow, "To", endDateField);
        addLabeled(secondaryRow, "Sort", sortOrder);
        SecondaryButton refresh = new SecondaryButton("Refresh");
        refresh.addActionListener(event -> refreshTransactions());
        secondaryRow.add(refresh);
        filters.add(primaryRow);
        filters.add(secondaryRow);

        searchField.getDocument().addDocumentListener(new FilterListener());
        accountFilter.addActionListener(event -> applyFilters());
        categoryFilter.addActionListener(event -> applyFilters());
        typeFilter.addActionListener(event -> applyFilters());
        sortOrder.addActionListener(event -> applyFilters());
        startDateField.addActionListener(event -> applyFilters());
        endDateField.addActionListener(event -> applyFilters());
        return filters;
    }

    private static void addLabeled(
            JPanel panel, String text, Component component) {
        JLabel label = new JLabel(text);
        label.setLabelFor(component);
        panel.add(label);
        panel.add(component);
    }

    private void populateFilters(Object selectedAccount, Object selectedCategory) {
        accountFilter.setModel(new DefaultComboBoxModel<>());
        accountFilter.addItem(ALL);
        accountService.listAllAccounts().forEach(accountFilter::addItem);
        restoreSelection(accountFilter, selectedAccount);
        categoryFilter.setModel(new DefaultComboBoxModel<>());
        categoryFilter.addItem(ALL);
        categoryService.listAllCategories().forEach(categoryFilter::addItem);
        restoreSelection(categoryFilter, selectedCategory);
    }

    private static void restoreSelection(JComboBox<Object> comboBox, Object value) {
        if (value != null) {
            comboBox.setSelectedItem(value);
        }
        if (comboBox.getSelectedIndex() < 0) {
            comboBox.setSelectedIndex(0);
        }
    }

    private void applyFilters() {
        try {
            LocalDate start = parseOptionalDate(startDateField.getText());
            LocalDate end = parseOptionalDate(endDateField.getText());
            if (start != null && end != null && start.isAfter(end)) {
                throw new IllegalArgumentException(
                        "The start date must not be after the end date.");
            }
            String search = searchField.getText().strip()
                    .toLowerCase(Locale.ROOT);
            Account account = accountFilter.getSelectedItem() instanceof Account item
                    ? item : null;
            Category category =
                    categoryFilter.getSelectedItem() instanceof Category item
                    ? item : null;
            TransactionRow.Type type =
                    typeFilter.getSelectedItem() instanceof TransactionRow.Type item
                    ? item : null;

            List<TransactionRow> rows = loadRows().stream()
                    .filter(row -> type == null || row.type() == type)
                    .filter(row -> row.involves(account))
                    .filter(row -> category == null
                            || category.equals(row.category()))
                    .filter(row -> start == null || !row.date().isBefore(start))
                    .filter(row -> end == null || !row.date().isAfter(end))
                    .filter(row -> matchesSearch(row, search))
                    .sorted(((SortOrder) sortOrder.getSelectedItem()).comparator)
                    .toList();
            tableModel.setRows(rows);
            contentLayout.show(contentPanel, rows.isEmpty() ? "empty" : "table");
            statusLabel.setForeground(AppColors.secondaryText());
            statusLabel.setText(rows.size() + (rows.size() == 1
                    ? " transaction" : " transactions"));
            updateSelectionActions();
        } catch (DateTimeParseException exception) {
            showFilterError("Use yyyy-MM-dd for both date filters.");
        } catch (RuntimeException exception) {
            showFilterError(exception.getMessage());
        }
    }

    private List<TransactionRow> loadRows() {
        List<TransactionRow> rows = new ArrayList<>();
        expenseService.getAllExpenses().stream()
                .map(TransactionRow::from).forEach(rows::add);
        incomeService.getAllIncome().stream()
                .map(TransactionRow::from).forEach(rows::add);
        transferService.getAllTransfers().stream()
                .map(TransactionRow::from).forEach(rows::add);
        return rows;
    }

    private static boolean matchesSearch(TransactionRow row, String search) {
        return search.isEmpty()
                || contains(row.description(), search)
                || contains(row.accountDisplay(), search)
                || contains(row.categoryDisplay(), search)
                || contains(row.type().toString(), search)
                || contains(row.identifier(), search);
    }

    private static boolean contains(String value, String search) {
        return value.toLowerCase(Locale.ROOT).contains(search);
    }

    private static LocalDate parseOptionalDate(String text) {
        String value = text == null ? "" : text.strip();
        return value.isEmpty() ? null : LocalDate.parse(value);
    }

    private void showFilterError(String message) {
        tableModel.setRows(List.of());
        contentLayout.show(contentPanel, "empty");
        statusLabel.setForeground(AppColors.expense());
        statusLabel.setText(message == null ? "Unable to apply filters." : message);
    }

    private TransactionRow selectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.getRow(table.convertRowIndexToModel(viewRow));
    }

    private void updateSelectionActions() {
        boolean selected = table.getSelectedRow() >= 0;
        editButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
    }

    private void editSelected() {
        TransactionRow row = selectedRow();
        if (row == null) {
            return;
        }
        switch (row.type()) {
            case EXPENSE -> openExpense((Expense) row.source());
            case INCOME -> openIncome((Income) row.source());
            case TRANSFER -> openTransfer((Transfer) row.source());
        }
    }

    private void deleteSelected() {
        TransactionRow row = selectedRow();
        if (row == null || !ConfirmationDialogs.confirmDestructive(
                this,
                "Delete Transaction",
                "Delete the selected " + row.type().toString().toLowerCase(
                        Locale.ROOT) + "? This cannot be undone.")) {
            return;
        }
        try {
            boolean deleted = switch (row.type()) {
                case EXPENSE -> expenseService.deleteExpense(row.identifier());
                case INCOME -> incomeService.deleteIncome(row.identifier());
                case TRANSFER -> transferService.deleteTransfer(row.identifier());
            };
            if (!deleted) {
                throw new IllegalStateException(
                        "The selected transaction no longer exists.");
            }
            afterMutation("Transaction deleted.");
        } catch (RuntimeException exception) {
            ConfirmationDialogs.showError(
                    this, "Unable to Delete Transaction", exception);
        }
    }

    private void openExpense(Expense expense) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (new ExpenseFormDialog(owner, expenseService, expense,
                categoryService.listSelectableCategories(),
                accountService.listSelectableAccounts(),
                accountService.getDefaultAccount()).showDialog()) {
            afterMutation(expense == null ? "Expense added." : "Expense updated.");
        }
    }

    private void openIncome(Income income) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (new IncomeFormDialog(owner, incomeService, income,
                accountService.listSelectableAccounts(),
                accountService.getDefaultAccount()).showDialog()) {
            afterMutation(income == null ? "Income added." : "Income updated.");
        }
    }

    private void openTransfer(Transfer transfer) {
        try {
            Window owner = SwingUtilities.getWindowAncestor(this);
            if (new TransferFormDialog(owner, transferService, transfer,
                    accountService.listSelectableAccounts(),
                    accountService.getDefaultAccount()).showDialog()) {
                afterMutation(transfer == null
                        ? "Transfer added." : "Transfer updated.");
            }
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Transfer Requires Two Accounts",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void afterMutation(String message) {
        refreshTransactions();
        dataChangedListener.run();
        statusLabel.setForeground(AppColors.income());
        statusLabel.setText(message);
    }

    private enum SortOrder {
        NEWEST("Newest first", Comparator.comparing(
                TransactionRow::date).reversed()
                .thenComparing(TransactionRow::identifier)),
        OLDEST("Oldest first", Comparator.comparing(TransactionRow::date)
                .thenComparing(TransactionRow::identifier)),
        AMOUNT_HIGH("Amount: high to low", Comparator.comparing(
                TransactionRow::amount).reversed()),
        AMOUNT_LOW("Amount: low to high", Comparator.comparing(
                TransactionRow::amount)),
        DESCRIPTION("Description: A to Z", Comparator.comparing(
                row -> row.description().toLowerCase(Locale.ROOT)));

        private final String label;
        private final Comparator<TransactionRow> comparator;

        SortOrder(String label, Comparator<TransactionRow> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final class FilterListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent event) {
            applyFilters();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            applyFilters();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            applyFilters();
        }
    }

    private static final class TypeRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!selected && value instanceof TransactionRow.Type type) {
                setForeground(colorFor(type));
            }
            return this;
        }
    }

    private static final class AmountRenderer extends DefaultTableCellRenderer {

        AmountRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            TransactionRow transaction =
                    ((TransactionTableModel) table.getModel()).getRow(modelRow);
            setText(transaction.amountDisplay());
            setFont(getFont().deriveFont(Font.BOLD));
            if (!selected) {
                setForeground(colorFor(transaction.type()));
            }
            return this;
        }
    }

    private static Color colorFor(TransactionRow.Type type) {
        return switch (type) {
            case INCOME -> AppColors.income();
            case EXPENSE -> AppColors.expense();
            case TRANSFER -> AppColors.transfer();
        };
    }
}
