package com.spendwise.ui;

import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class TransferTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Date", "From", "To", "Amount", "Tags", "Note"
    };
    private List<Transfer> entries = List.of();

    void replace(List<Transfer> newEntries) {
        entries = List.copyOf(Objects.requireNonNull(
                newEntries, "Transfers are required."));
        fireTableDataChanged();
    }

    Transfer getTransferAt(int row) {
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
            case 3 -> BigDecimal.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        Transfer transfer = getTransferAt(row);
        return switch (column) {
            case 0 -> transfer.getDate();
            case 1 -> transfer.getSourceAccount().getDisplayName();
            case 2 -> transfer.getDestinationAccount().getDisplayName();
            case 3 -> transfer.getAmount();
            case 4 -> String.join(", ", transfer.getTags());
            case 5 -> transfer.getNote();
            default -> throw new IndexOutOfBoundsException(
                    "Transfer column is out of range: " + column);
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
