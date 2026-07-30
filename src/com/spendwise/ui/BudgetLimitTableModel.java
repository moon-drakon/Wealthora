package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.service.BudgetAlertLevel;
import com.spendwise.service.BudgetStatusSnapshot;
import com.spendwise.service.BudgetUsage;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class BudgetLimitTableModel extends AbstractTableModel {

    private static final int LIMIT_COLUMN = 2;
    private static final String[] COLUMN_NAMES = {
        "Category", "Spent", "Limit", "Remaining", "Status"
    };

    private final Map<Category, BudgetUsage> usageByCategory =
            new EnumMap<>(Category.class);
    private final Object[] limitValues = new Object[Category.values().length];

    BudgetLimitTableModel() {
        for (Category category : Category.values()) {
            usageByCategory.put(
                    category,
                    new BudgetUsage(new BigDecimal("0.00"), java.util.Optional.empty()));
            limitValues[category.ordinal()] = "";
        }
    }

    void replaceStatus(BudgetStatusSnapshot snapshot) {
        BudgetStatusSnapshot requiredSnapshot = Objects.requireNonNull(
                snapshot, "Budget status snapshot is required.");
        for (Category category : Category.values()) {
            BudgetUsage usage = requiredSnapshot.getUsageForCategory(category);
            usageByCategory.put(category, usage);
            limitValues[category.ordinal()] =
                    usage.getLimit().<Object>map(value -> value).orElse("");
        }
        fireTableDataChanged();
    }

    Category getCategoryAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= getRowCount()) {
            throw new IndexOutOfBoundsException(
                    "Budget row index is out of range: " + rowIndex);
        }
        return Category.values()[rowIndex];
    }

    BudgetUsage getUsageAt(int rowIndex) {
        return usageByCategory.get(getCategoryAt(rowIndex));
    }

    String getLimitTextAt(int rowIndex) {
        Object value = limitValues[getCategoryAt(rowIndex).ordinal()];
        return value == null ? "" : value.toString();
    }

    Object getLimitValueAt(int rowIndex) {
        return limitValues[getCategoryAt(rowIndex).ordinal()];
    }

    void restoreLimitValues(Object[] values) {
        Objects.requireNonNull(values, "Budget limit values are required.");
        if (values.length != limitValues.length) {
            throw new IllegalArgumentException(
                    "Budget limit value count is incorrect.");
        }
        System.arraycopy(values, 0, limitValues, 0, values.length);
        fireTableRowsUpdated(0, getRowCount() - 1);
    }

    Object[] copyLimitValues() {
        return limitValues.clone();
    }

    @Override
    public int getRowCount() {
        return Category.values().length;
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
        return switch (columnIndex) {
            case 0 -> String.class;
            case 1 -> BigDecimal.class;
            case 2, 3 -> Object.class;
            case 4 -> BudgetAlertLevel.class;
            default -> throw new IndexOutOfBoundsException(
                    "Budget column index is out of range: " + columnIndex);
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Category category = getCategoryAt(rowIndex);
        BudgetUsage usage = usageByCategory.get(category);
        return switch (columnIndex) {
            case 0 -> category.getDisplayName();
            case 1 -> usage.getSpent();
            case 2 -> limitValues[rowIndex];
            case 3 -> usage.getRemaining().<Object>map(value -> value).orElse("Not set");
            case 4 -> usage.getAlertLevel();
            default -> throw new IndexOutOfBoundsException(
                    "Budget column index is out of range: " + columnIndex);
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == LIMIT_COLUMN;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        getCategoryAt(rowIndex);
        if (columnIndex != LIMIT_COLUMN) {
            return;
        }
        limitValues[rowIndex] = value == null ? "" : value;
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}
