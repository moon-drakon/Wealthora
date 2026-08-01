package com.spendwise.ui;

import com.spendwise.model.Income;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class IncomeTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Date", "Source", "Account", "Amount", "Note"
    };
    private List<Income> entries = List.of();

    void replace(List<Income> newEntries) {
        entries = List.copyOf(Objects.requireNonNull(
                newEntries, "Income entries are required."));
        fireTableDataChanged();
    }

    Income getIncomeAt(int row) {
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
        Income income = getIncomeAt(row);
        return switch (column) {
            case 0 -> income.getDate();
            case 1 -> income.getSource();
            case 2 -> income.getAccount().getDisplayName();
            case 3 -> income.getAmount();
            case 4 -> income.getNote();
            default -> throw new IndexOutOfBoundsException(
                    "Income column is out of range: " + column);
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
