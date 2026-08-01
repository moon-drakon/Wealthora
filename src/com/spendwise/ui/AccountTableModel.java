package com.spendwise.ui;

import com.spendwise.model.Account;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class AccountTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Account", "Type", "Opening Balance", "Current Balance",
        "Default", "Status"
    };
    private List<Account> accounts = List.of();
    private Map<Account, BigDecimal> balances = Map.of();
    private Account defaultAccount = Account.DEFAULT;

    void replace(
            List<Account> newAccounts,
            Map<Account, BigDecimal> newBalances) {
        replace(newAccounts, newBalances, Account.DEFAULT);
    }

    void replace(
            List<Account> newAccounts,
            Map<Account, BigDecimal> newBalances,
            Account newDefaultAccount) {
        accounts = List.copyOf(Objects.requireNonNull(
                newAccounts, "Accounts are required."));
        balances = Map.copyOf(Objects.requireNonNull(
                newBalances, "Account balances are required."));
        defaultAccount = Objects.requireNonNull(
                newDefaultAccount, "Default account is required.");
        fireTableDataChanged();
    }

    Account getAccountAt(int row) {
        return accounts.get(row);
    }

    @Override
    public int getRowCount() {
        return accounts.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 2 || column == 3
                ? BigDecimal.class
                : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Account account = getAccountAt(row);
        return switch (column) {
            case 0 -> account.getDisplayName();
            case 1 -> account.getType().getDisplayName();
            case 2 -> account.getOpeningBalance();
            case 3 -> balances.get(account);
            case 4 -> account.equals(defaultAccount) ? "Yes" : "";
            case 5 -> account.isProtected()
                    ? "Protected"
                    : account.isActive() ? "Active" : "Archived";
            default -> throw new IndexOutOfBoundsException(
                    "Account column is out of range: " + column);
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
