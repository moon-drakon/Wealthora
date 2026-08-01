package com.spendwise.ui;

import com.spendwise.service.FinancialActivityEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class FinancialActivityTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Date", "Type", "Description", "Account", "Category", "Amount"
    };
    private List<FinancialActivityEntry> entries = List.of();

    void replaceEntries(List<FinancialActivityEntry> newEntries) {
        entries = List.copyOf(Objects.requireNonNull(
                newEntries, "Financial activity entries are required."));
        fireTableDataChanged();
    }

    FinancialActivityEntry getEntryAt(int row) {
        return entries.get(row);
    }

    @Override
    public int getRowCount() {
        return entries.size();
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
        return switch (column) {
            case 0 -> LocalDate.class;
            case 5 -> BigDecimal.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        FinancialActivityEntry entry = getEntryAt(row);
        return switch (column) {
            case 0 -> entry.getDate();
            case 1 -> entry.getType().getDisplayName();
            case 2 -> entry.getDescription();
            case 3 -> entry.getDestinationAccount()
                    .map(destination -> entry.getAccount().getDisplayName()
                        + " → " + destination.getDisplayName())
                    .orElse(entry.getAccount().getDisplayName());
            case 4 -> entry.getCategory()
                    .map(category -> category.getDisplayName())
                    .orElse("");
            case 5 -> entry.getAmount();
            default -> throw new IndexOutOfBoundsException(
                    "Activity column is out of range: " + column);
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
