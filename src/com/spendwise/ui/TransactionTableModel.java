package com.spendwise.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.spendwise.model.TransactionType;
import javax.swing.table.AbstractTableModel;

final class TransactionTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Date", "Type", "Description", "Category", "Account", "Amount",
        "Payment Method", "Tags"
    };
    private List<TransactionRow> rows = List.of();

    void setRows(List<TransactionRow> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    TransactionRow getRow(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
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
            case 1 -> TransactionType.class;
            case 5 -> BigDecimal.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        TransactionRow transaction = rows.get(row);
        return switch (column) {
            case 0 -> transaction.date();
            case 1 -> transaction.type();
            case 2 -> transaction.description();
            case 3 -> transaction.categoryDisplay();
            case 4 -> transaction.accountDisplay();
            case 5 -> transaction.amount();
            case 6 -> transaction.paymentMethodDisplay();
            case 7 -> transaction.tagsDisplay();
            default -> throw new IndexOutOfBoundsException(column);
        };
    }
}
