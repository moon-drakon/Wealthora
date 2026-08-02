package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import com.spendwise.model.PaymentMethod;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AccountArchiveResult;
import com.spendwise.service.AccountBalanceSnapshot;
import com.spendwise.service.AccountService;
import com.spendwise.service.AccountStatementService;
import com.spendwise.service.AccountStatementSnapshot;
import com.spendwise.service.FinanceNotFoundException;
import com.spendwise.service.FinanceService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.IncomeSortOrder;
import com.spendwise.service.TransferService;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

public final class FinancePanel extends JPanel {

    private final AccountService accountService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final FinanceService financeService;
    private final AccountStatementService accountStatementService;
    private final Runnable financeChangeListener;
    private final AccountTableModel accountModel = new AccountTableModel();
    private final IncomeTableModel incomeModel = new IncomeTableModel();
    private final TransferTableModel transferModel = new TransferTableModel();
    private final FinancialActivityTableModel statementModel =
            new FinancialActivityTableModel();
    private final JTable accountTable = table(accountModel);
    private final JTable incomeTable = table(incomeModel);
    private final JTable transferTable = table(transferModel);
    private final JTable statementTable = table(statementModel);
    private final JLabel totalBalanceLabel = new JLabel("Total balance: 0.00");
    private final JLabel statusLabel = new JLabel("Finance data has not loaded.");
    private final JTextField incomeSearchField = new JTextField(18);
    private final JComboBox<AccountFilter> accountFilterBox =
            new JComboBox<>(AccountFilter.values());
    private final JLabel statementSummaryLabel =
            new JLabel("Select an account to view its activity.");

    public FinancePanel(
            AccountService accountService,
            IncomeService incomeService,
            TransferService transferService,
            FinanceService financeService,
            Runnable financeChangeListener) {
        this(
                accountService,
                incomeService,
                transferService,
                financeService,
                null,
                financeChangeListener);
    }

    public FinancePanel(
            AccountService accountService,
            IncomeService incomeService,
            TransferService transferService,
            FinanceService financeService,
            AccountStatementService accountStatementService,
            Runnable financeChangeListener) {
        requireEventDispatchThread();
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.financeService = Objects.requireNonNull(
                financeService, "Finance service is required.");
        this.accountStatementService = accountStatementService;
        this.financeChangeListener = Objects.requireNonNull(
                financeChangeListener, "Finance change listener is required.");
        buildInterface();
        accountTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                refreshAccountStatement();
            }
        });
        refreshFinanceData();
    }

    public void refreshFinanceData() {
        requireEventDispatchThread();
        refreshFinanceDataSafely();
    }

    boolean refreshIncomeData() {
        requireEventDispatchThread();
        try {
            incomeModel.replace(incomeService.findIncome(
                    incomeSearchField.getText(),
                    null,
                    null,
                    null,
                    IncomeSortOrder.DATE_NEWEST_FIRST));
            statusLabel.setText("Income records refreshed.");
            return true;
        } catch (ValidationException | RepositoryException exception) {
            statusLabel.setText(
                    "Unable to load income data: " + safeMessage(exception));
            return false;
        }
    }

    private boolean refreshFinanceDataSafely() {
        String selectedAccountIdentifier = selectedAccountIdentifier();
        try {
            AccountBalanceSnapshot balances =
                    financeService.calculateBalances();
            List<Account> accounts = accountService.listAllAccounts();
            List<Income> incomeEntries = incomeService.findIncome(
                    incomeSearchField.getText(),
                    null,
                    null,
                    null,
                    IncomeSortOrder.DATE_NEWEST_FIRST);
            List<Transfer> transfers = transferService.getAllTransfers();
            Account defaultAccount = accountService.getDefaultAccount();
            accountModel.replace(
                    filteredAccounts(accounts),
                    balances.getBalances(),
                    defaultAccount);
            incomeModel.replace(incomeEntries);
            transferModel.replace(transfers);
            restoreAccountSelection(selectedAccountIdentifier);
            totalBalanceLabel.setText(
                    "Total balance: "
                    + balances.getTotalBalance().toPlainString());
            statusLabel.setText("Accounts and transactions refreshed.");
            return true;
        } catch (RepositoryException exception) {
            statusLabel.setText(
                    "Unable to load finance data: " + safeMessage(exception));
            return false;
        }
    }

    int getAccountRowCount() {
        return accountModel.getRowCount();
    }

    int getIncomeRowCount() {
        return incomeModel.getRowCount();
    }

    int getTransferRowCount() {
        return transferModel.getRowCount();
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    int getStatementRowCount() {
        return statementModel.getRowCount();
    }

    String getStatementSummaryText() {
        return statementSummaryLabel.getText();
    }

    void selectAccount(String identifier) {
        for (int row = 0; row < accountModel.getRowCount(); row++) {
            if (accountModel.getAccountAt(row).getIdentifier().equals(identifier)) {
                int viewRow = accountTable.convertRowIndexToView(row);
                accountTable.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    void setAccountFilter(String label) {
        for (AccountFilter filter : AccountFilter.values()) {
            if (filter.toString().equals(label)) {
                accountFilterBox.setSelectedItem(filter);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown account filter: " + label);
    }

    private void buildInterface() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(new Color(244, 247, 250));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Accounts, Income and Transfers");
        title.setFont(title.getFont().deriveFont(22f));
        heading.add(title, BorderLayout.WEST);
        heading.add(totalBalanceLabel, BorderLayout.EAST);

        JTabbedPane financeTabs = new JTabbedPane();
        financeTabs.addTab("Accounts", createAccountsTab());
        financeTabs.addTab("Income", createIncomeTab());
        financeTabs.addTab("Transfers", createTransfersTab());

        add(heading, BorderLayout.NORTH);
        add(financeTabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createAccountsTab() {
        JPanel panel = contentPanel();
        JPanel filters = actionPanel();
        filters.add(new JLabel("Show:"));
        filters.add(accountFilterBox);
        accountFilterBox.addActionListener(event -> refreshFinanceDataSafely());
        panel.add(filters, BorderLayout.NORTH);

        JPanel accountContent = new JPanel(new GridLayout(2, 1, 8, 8));
        accountContent.add(new JScrollPane(accountTable));
        JPanel statementPanel = new JPanel(new BorderLayout(6, 6));
        statementPanel.setBorder(BorderFactory.createTitledBorder(
                "Selected Account Activity"));
        statementPanel.add(statementSummaryLabel, BorderLayout.NORTH);
        statementPanel.add(new JScrollPane(statementTable), BorderLayout.CENTER);
        accountContent.add(statementPanel);
        panel.add(accountContent, BorderLayout.CENTER);
        JPanel actions = actionPanel();
        actions.add(button("Add Account", this::addAccount));
        actions.add(button("Edit Account", this::editAccount));
        actions.add(button("Set Default", this::setDefaultAccount));
        actions.add(button("Archive", this::archiveAccount));
        actions.add(button("Restore", this::restoreAccount));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createIncomeTab() {
        JPanel panel = contentPanel();
        JPanel filters = actionPanel();
        filters.add(new JLabel("Search:"));
        filters.add(incomeSearchField);
        filters.add(button("Apply", this::refreshIncome));
        filters.add(button("Clear", () -> {
            incomeSearchField.setText("");
            refreshIncome();
        }));
        panel.add(filters, BorderLayout.NORTH);
        panel.add(new JScrollPane(incomeTable), BorderLayout.CENTER);
        JPanel actions = actionPanel();
        actions.add(button("Add Income", () -> editIncome(null)));
        actions.add(button("Edit", this::editSelectedIncome));
        actions.add(button("Delete", this::deleteIncome));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createTransfersTab() {
        JPanel panel = contentPanel();
        panel.add(new JScrollPane(transferTable), BorderLayout.CENTER);
        JPanel actions = actionPanel();
        actions.add(button("Add Transfer", () -> editTransfer(null)));
        actions.add(button("Edit", this::editSelectedTransfer));
        actions.add(button("Delete", this::deleteTransfer));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void addAccount() {
        JTextField name = new JTextField();
        JComboBox<AccountType> type =
                new JComboBox<>(AccountType.values());
        JTextField opening = new JTextField("0.00");
        JTextField icon = new JTextField(Account.DEFAULT_ICON);
        JTextField color = new JTextField(Account.DEFAULT_COLOR);
        JPanel form = form(
                "Name", name,
                "Type", type,
                "Opening balance", opening,
                "Icon name", icon,
                "Color (#RRGGBB)", color);
        while (JOptionPane.showConfirmDialog(
                this,
                form,
                "Add Account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                accountService.addAccount(
                        name.getText(),
                        (AccountType) type.getSelectedItem(),
                        new BigDecimal(opening.getText().strip()),
                        icon.getText(),
                        color.getText());
                mutationSucceeded("Account added.");
                return;
            } catch (NumberFormatException exception) {
                showError("Opening balance must be a decimal number.");
            } catch (ValidationException | RepositoryException exception) {
                showError(safeMessage(exception));
            }
        }
    }

    private void editAccount() {
        Account account = selectedAccount();
        if (account == null) {
            showInformation("Select an account to edit.");
            return;
        }
        JTextField name = new JTextField(account.getDisplayName());
        JComboBox<AccountType> type = new JComboBox<>(AccountType.values());
        type.setSelectedItem(account.getType());
        JTextField opening = new JTextField(
                account.getOpeningBalance().toPlainString());
        JTextField icon = new JTextField(account.getIconName());
        JTextField color = new JTextField(account.getColorHex());
        JPanel form = form(
                "Name", name,
                "Type", type,
                "Opening balance", opening,
                "Icon name", icon,
                "Color (#RRGGBB)", color);
        if (JOptionPane.showConfirmDialog(
                this,
                form,
                "Edit Account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            accountService.updateAccountDetails(
                    account.getIdentifier(),
                    name.getText(),
                    (AccountType) type.getSelectedItem(),
                    new BigDecimal(opening.getText().strip()),
                    icon.getText(),
                    color.getText());
            mutationSucceeded("Account updated.");
        } catch (NumberFormatException exception) {
            showError("Opening balance must be a decimal number.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void setDefaultAccount() {
        Account account = selectedAccount();
        if (account == null) {
            showInformation("Select an active account to make the default.");
            return;
        }
        try {
            Account selected = accountService.setDefaultAccount(
                    account.getIdentifier());
            mutationSucceeded(
                    selected.getDisplayName() + " is now the default account.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void archiveAccount() {
        Account account = selectedAccount();
        if (account == null) {
            showInformation("Select an account to archive.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                this,
                "Archive \"" + account.getDisplayName() + "\"?",
                "Confirm Archive",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            AccountArchiveResult result = accountService
                    .archiveAccountWithResult(account.getIdentifier());
            String message = result.replacementDefault()
                    .map(replacement -> "Account archived. Default changed to "
                        + replacement.getDisplayName() + ".")
                    .orElse("Account archived.");
            mutationSucceeded(message);
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void restoreAccount() {
        Account account = selectedAccount();
        if (account == null) {
            showInformation("Select an account to restore.");
            return;
        }
        try {
            accountService.restoreAccount(account.getIdentifier());
            mutationSucceeded("Account restored.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void editIncome(Income existing) {
        List<Account> availableAccounts;
        Account preferredAccount;
        try {
            availableAccounts = selectableAccounts(existing == null
                    ? null : existing.getAccount());
            preferredAccount = existing == null
                    ? accountService.getDefaultAccount()
                    : existing.getAccount();
        } catch (ValidationException | RepositoryException exception) {
            showError("Unable to load account choices: "
                    + safeMessage(exception));
            return;
        }
        JTextField date = new JTextField(
                existing == null
                        ? LocalDate.now().toString()
                        : existing.getDate().toString());
        JTextField amount = new JTextField(
                existing == null
                        ? ""
                        : existing.getAmount().toPlainString());
        JTextField source = new JTextField(
                existing == null ? "" : existing.getSource());
        JComboBox<Account> account = new JComboBox<>(
                availableAccounts.toArray(Account[]::new));
        JTextField note = new JTextField(
                existing == null ? "" : existing.getNote());
        JComboBox<PaymentMethod> paymentMethod =
                new JComboBox<>(PaymentMethod.values());
        JTextField tags = new JTextField(existing == null
                ? "" : String.join(", ", existing.getTags()));
        if (existing != null) {
            paymentMethod.setSelectedItem(existing.getPaymentMethod());
        }
        account.setSelectedItem(preferredAccount);
        JPanel form = form(
                "Date (yyyy-MM-dd)", date,
                "Amount", amount,
                "Source", source,
                "Account", account,
                "Payment method", paymentMethod,
                "Tags", tags,
                "Note", note);
        String title = existing == null ? "Add Income" : "Edit Income";
        while (JOptionPane.showConfirmDialog(
                this,
                form,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                if (existing == null) {
                    incomeService.createIncome(
                            LocalDate.parse(date.getText().strip()),
                            new BigDecimal(amount.getText().strip()),
                            source.getText(),
                            (Account) account.getSelectedItem(),
                            (PaymentMethod) paymentMethod.getSelectedItem(),
                            ExpenseFormDialog.parseTags(tags.getText()),
                            note.getText());
                } else {
                    incomeService.updateIncome(
                            existing.getId(),
                            LocalDate.parse(date.getText().strip()),
                            new BigDecimal(amount.getText().strip()),
                            source.getText(),
                            (Account) account.getSelectedItem(),
                            (PaymentMethod) paymentMethod.getSelectedItem(),
                            ExpenseFormDialog.parseTags(tags.getText()),
                            note.getText());
                }
                mutationSucceeded(existing == null
                        ? "Income added."
                        : "Income updated.");
                return;
            } catch (NumberFormatException | DateTimeParseException exception) {
                showError("Enter a valid date and decimal amount.");
            } catch (ValidationException
                    | FinanceNotFoundException
                    | RepositoryException exception) {
                showError(safeMessage(exception));
            }
        }
    }

    private void editSelectedIncome() {
        Income income = selectedIncome();
        if (income == null) {
            showInformation("Select an income entry to edit.");
            return;
        }
        editIncome(income);
    }

    private void deleteIncome() {
        Income income = selectedIncome();
        if (income == null) {
            showInformation("Select an income entry to delete.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                this,
                "Delete income from \"" + income.getSource() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            boolean deleted = incomeService.deleteIncome(income.getId());
            mutationSucceeded(deleted
                    ? "Income deleted."
                    : "Income no longer exists; finance data refreshed.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void editTransfer(Transfer existing) {
        List<Account> sourceAccounts;
        List<Account> destinationAccounts;
        Account preferredSource;
        try {
            sourceAccounts = selectableAccounts(existing == null
                    ? null : existing.getSourceAccount());
            destinationAccounts = selectableAccounts(existing == null
                    ? null : existing.getDestinationAccount());
            preferredSource = existing == null
                    ? accountService.getDefaultAccount()
                    : existing.getSourceAccount();
        } catch (ValidationException | RepositoryException exception) {
            showError("Unable to load account choices: "
                    + safeMessage(exception));
            return;
        }
        JTextField date = new JTextField(
                existing == null
                        ? LocalDate.now().toString()
                        : existing.getDate().toString());
        JTextField amount = new JTextField(
                existing == null
                        ? ""
                        : existing.getAmount().toPlainString());
        JComboBox<Account> source = new JComboBox<>(
                sourceAccounts.toArray(Account[]::new));
        JComboBox<Account> destination = new JComboBox<>(
                destinationAccounts.toArray(Account[]::new));
        JTextField note = new JTextField(
                existing == null ? "" : existing.getNote());
        JTextField tags = new JTextField(existing == null
                ? "" : String.join(", ", existing.getTags()));
        source.setSelectedItem(preferredSource);
        if (existing != null) {
            destination.setSelectedItem(existing.getDestinationAccount());
        } else {
            selectDifferentAccount(destination, (Account) source.getSelectedItem());
        }
        JPanel form = form(
                "Date (yyyy-MM-dd)", date,
                "Amount", amount,
                "From", source,
                "To", destination,
                "Tags", tags,
                "Note", note);
        String title = existing == null ? "Add Transfer" : "Edit Transfer";
        while (JOptionPane.showConfirmDialog(
                this,
                form,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                if (existing == null) {
                    transferService.createTransfer(
                            LocalDate.parse(date.getText().strip()),
                            new BigDecimal(amount.getText().strip()),
                            (Account) source.getSelectedItem(),
                            (Account) destination.getSelectedItem(),
                            ExpenseFormDialog.parseTags(tags.getText()),
                            note.getText());
                } else {
                    transferService.updateTransfer(
                            existing.getId(),
                            LocalDate.parse(date.getText().strip()),
                            new BigDecimal(amount.getText().strip()),
                            (Account) source.getSelectedItem(),
                            (Account) destination.getSelectedItem(),
                            ExpenseFormDialog.parseTags(tags.getText()),
                            note.getText());
                }
                mutationSucceeded(existing == null
                        ? "Transfer added."
                        : "Transfer updated.");
                return;
            } catch (NumberFormatException | DateTimeParseException exception) {
                showError("Enter a valid date and decimal amount.");
            } catch (ValidationException
                    | FinanceNotFoundException
                    | RepositoryException exception) {
                showError(safeMessage(exception));
            }
        }
    }

    private void editSelectedTransfer() {
        Transfer transfer = selectedTransfer();
        if (transfer == null) {
            showInformation("Select a transfer to edit.");
            return;
        }
        editTransfer(transfer);
    }

    private void deleteTransfer() {
        Transfer transfer = selectedTransfer();
        if (transfer == null) {
            showInformation("Select a transfer to delete.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                this,
                "Delete this transfer?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            boolean deleted = transferService.deleteTransfer(transfer.getId());
            mutationSucceeded(deleted
                    ? "Transfer deleted."
                    : "Transfer no longer exists; finance data refreshed.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void refreshIncome() {
        refreshIncomeData();
    }

    void mutationSucceeded(String message) {
        boolean localRefreshSucceeded = refreshFinanceDataSafely();
        boolean downstreamRefreshSucceeded = true;
        try {
            financeChangeListener.run();
        } catch (RuntimeException exception) {
            downstreamRefreshSucceeded = false;
        }
        statusLabel.setText(
                localRefreshSucceeded && downstreamRefreshSucceeded
                        ? message
                        : message
                        + " One or more views could not refresh; "
                        + "use the tab to retry.");
    }

    private Account selectedAccount() {
        int row = accountTable.getSelectedRow();
        return row < 0
                ? null
                : accountModel.getAccountAt(
                        accountTable.convertRowIndexToModel(row));
    }

    private String selectedAccountIdentifier() {
        Account selected = selectedAccount();
        return selected == null ? null : selected.getIdentifier();
    }

    private List<Account> filteredAccounts(List<Account> accounts) {
        AccountFilter selected = (AccountFilter) accountFilterBox.getSelectedItem();
        AccountFilter filter = selected == null ? AccountFilter.ALL : selected;
        return accounts.stream().filter(filter::includes).toList();
    }

    private void restoreAccountSelection(String identifier) {
        if (identifier != null) {
            selectAccount(identifier);
        }
        if (accountTable.getSelectedRow() < 0 && accountModel.getRowCount() > 0) {
            accountTable.setRowSelectionInterval(0, 0);
        } else if (accountModel.getRowCount() == 0) {
            statementModel.replaceEntries(List.of());
            statementSummaryLabel.setText(
                    "No account is available for this filter.");
        }
    }

    private void refreshAccountStatement() {
        Account account = selectedAccount();
        if (account == null || accountStatementService == null) {
            statementModel.replaceEntries(List.of());
            statementSummaryLabel.setText(account == null
                    ? "Select an account to view its activity."
                    : "Account activity is unavailable in this view.");
            return;
        }
        try {
            AccountStatementSnapshot statement =
                    accountStatementService.getStatement(
                            account.getIdentifier());
            statementModel.replaceEntries(statement.getEntries());
            statementSummaryLabel.setText(
                    "Opening: " + statement.getOpeningBalance().toPlainString()
                    + " | Income: " + statement.getIncome().toPlainString()
                    + " | Expenses: " + statement.getExpenses().toPlainString()
                    + " | Transfers in: "
                    + statement.getIncomingTransfers().toPlainString()
                    + " | Transfers out: "
                    + statement.getOutgoingTransfers().toPlainString()
                    + " | Current: "
                    + statement.getCurrentBalance().toPlainString());
        } catch (RepositoryException exception) {
            statementModel.replaceEntries(List.of());
            statementSummaryLabel.setText(
                    "Unable to load account activity: " + safeMessage(exception));
        }
    }

    private static void selectDifferentAccount(
            JComboBox<Account> choices, Account excluded) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (!Objects.equals(choices.getItemAt(index), excluded)) {
                choices.setSelectedIndex(index);
                return;
            }
        }
    }

    private Income selectedIncome() {
        int row = incomeTable.getSelectedRow();
        return row < 0
                ? null
                : incomeModel.getIncomeAt(
                        incomeTable.convertRowIndexToModel(row));
    }

    private Transfer selectedTransfer() {
        int row = transferTable.getSelectedRow();
        return row < 0
                ? null
                : transferModel.getTransferAt(
                        transferTable.convertRowIndexToModel(row));
    }

    private List<Account> selectableAccounts(Account historical) {
        List<Account> accounts =
                new java.util.ArrayList<>(
                        accountService.listSelectableAccounts());
        if (historical != null && !accounts.contains(historical)) {
            accounts.add(historical);
        }
        return List.copyOf(accounts);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(
                this,
                message,
                "Finance Operation Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private void showInformation(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "SpendWise",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static JPanel contentPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        return panel;
    }

    private static JPanel actionPanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JTable table(javax.swing.table.TableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private static JPanel form(Object... labelAndFieldPairs) {
        JPanel panel = new JPanel(new GridLayout(
                labelAndFieldPairs.length / 2, 2, 8, 8));
        for (int index = 0; index < labelAndFieldPairs.length; index += 2) {
            panel.add(new JLabel(labelAndFieldPairs[index] + ":"));
            panel.add((java.awt.Component) labelAndFieldPairs[index + 1]);
        }
        return panel;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The operation could not be completed safely."
                : message;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "FinancePanel must be created on the Event Dispatch Thread.");
        }
    }

    private enum AccountFilter {
        ALL("All accounts") {
            @Override
            boolean includes(Account account) {
                return true;
            }
        },
        ACTIVE("Active accounts") {
            @Override
            boolean includes(Account account) {
                return account.isActive();
            }
        },
        ARCHIVED("Archived accounts") {
            @Override
            boolean includes(Account account) {
                return account.isArchived();
            }
        };

        private final String label;

        AccountFilter(String label) {
            this.label = label;
        }

        abstract boolean includes(Account account);

        @Override
        public String toString() {
            return label;
        }
    }
}
