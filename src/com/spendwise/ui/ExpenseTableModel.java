package com.spendwise.ui;

import com.spendwise.model.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class ExpenseTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {
        "Date", "Description", "Category", "Amount", "Notes"
    };
    private static final Class<?>[] COLUMN_CLASSES = {
        LocalDate.class, String.class, String.class, BigDecimal.class, String.class
    };

    private List<Expense> expenses = List.of();

    void replaceExpenses(List<Expense> newExpenses) {
        Objects.requireNonNull(newExpenses, "Expense list is required.");
        for (Expense expense : newExpenses) {
            if (expense == null) {
                throw new IllegalArgumentException(
                        "Expense list cannot contain null elements.");
            }
        }
        expenses = List.copyOf(newExpenses);
        fireTableDataChanged();
    }

    Expense getExpenseAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= expenses.size()) {
            throw new IndexOutOfBoundsException(
                    "Expense row index is out of range: " + rowIndex);
        }
        return expenses.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return expenses.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return COLUMN_NAMES[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Expense expense = getExpenseAt(rowIndex);
        return switch (columnIndex) {
            case 0 -> expense.getDate();
            case 1 -> expense.getDescription();
            case 2 -> expense.getCategory().getDisplayName();
            case 3 -> expense.getAmount();
            case 4 -> expense.getNotes();
            default -> throw new IndexOutOfBoundsException(
                    "Expense column index is out of range: " + columnIndex);
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
