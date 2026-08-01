package com.spendwise.ui;

import com.spendwise.model.RecurringEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class RecurringEntryTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Type", "Description", "Amount", "Account", "Category / To",
        "Frequency", "Next Due", "Status"
    };
    private List<RecurringEntry> entries = List.of();

    void replace(List<RecurringEntry> newEntries) {
        entries = List.copyOf(Objects.requireNonNull(
                newEntries, "Recurring entries are required."));
        fireTableDataChanged();
    }

    RecurringEntry getEntryAt(int row) {
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
            case 2 -> BigDecimal.class;
            case 6 -> LocalDate.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        RecurringEntry entry = getEntryAt(row);
        return switch (column) {
            case 0 -> entry.getType().getDisplayName();
            case 1 -> entry.getDescription();
            case 2 -> entry.getAmount();
            case 3 -> entry.getSourceAccount().getDisplayName();
            case 4 -> entry.getDestinationAccount()
                    .map(account -> "To " + account.getDisplayName())
                    .orElseGet(() -> entry.getCategory()
                        .map(category -> category.getDisplayName())
                        .orElse(""));
            case 5 -> entry.getInterval() + " "
                    + entry.getFrequency().toString().toLowerCase();
            case 6 -> entry.getNextDueDate();
            case 7 -> entry.isActive() ? "Active" : "Inactive";
            default -> throw new IndexOutOfBoundsException(
                    "Recurring entry column is out of range: " + column);
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
