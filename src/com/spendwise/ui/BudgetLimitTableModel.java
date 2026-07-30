package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.service.BudgetAlertLevel;
import com.spendwise.service.BudgetStatusSnapshot;
import com.spendwise.service.BudgetUsage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.table.AbstractTableModel;

final class BudgetLimitTableModel extends AbstractTableModel {

    private static final int LIMIT_COLUMN = 2;
    private static final String[] COLUMN_NAMES = {
        "Category", "Spent", "Limit", "Remaining", "Status"
    };

    private List<Category> categories = List.of(Category.values());
    private final Map<Category, BudgetUsage> usageByCategory =
            new LinkedHashMap<>();
    private final Map<Category, Object> limitValues = new LinkedHashMap<>();

    BudgetLimitTableModel() {
        for (Category category : categories) {
            usageByCategory.put(category, emptyUsage());
            limitValues.put(category, "");
        }
    }

    void replaceStatus(BudgetStatusSnapshot snapshot) {
        replaceStatus(snapshot, List.of(Category.values()));
    }

    void replaceStatus(
            BudgetStatusSnapshot snapshot, List<Category> selectableCategories) {
        BudgetStatusSnapshot requiredSnapshot = Objects.requireNonNull(
                snapshot, "Budget status snapshot is required.");
        Objects.requireNonNull(
                selectableCategories, "Selectable categories are required.");

        LinkedHashSet<Category> orderedCategories =
                new LinkedHashSet<>(selectableCategories);
        orderedCategories.addAll(requiredSnapshot.getCategoryUsage().keySet());
        categories = List.copyOf(orderedCategories);
        usageByCategory.clear();
        limitValues.clear();
        for (Category category : categories) {
            BudgetUsage usage = requiredSnapshot.getCategoryUsage()
                    .getOrDefault(category, emptyUsage());
            usageByCategory.put(category, usage);
            limitValues.put(
                    category,
                    usage.getLimit().<Object>map(value -> value).orElse(""));
        }
        fireTableDataChanged();
    }

    Category getCategoryAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= getRowCount()) {
            throw new IndexOutOfBoundsException(
                    "Budget row index is out of range: " + rowIndex);
        }
        return categories.get(rowIndex);
    }

    BudgetUsage getUsageAt(int rowIndex) {
        return usageByCategory.get(getCategoryAt(rowIndex));
    }

    String getLimitTextAt(int rowIndex) {
        Object value = limitValues.get(getCategoryAt(rowIndex));
        return value == null ? "" : value.toString();
    }

    Object getLimitValueAt(int rowIndex) {
        return limitValues.get(getCategoryAt(rowIndex));
    }

    void restoreLimitValues(Object[] values) {
        Objects.requireNonNull(values, "Budget limit values are required.");
        if (values.length != categories.size()) {
            throw new IllegalArgumentException(
                    "Budget limit value count is incorrect.");
        }
        for (int index = 0; index < values.length; index++) {
            limitValues.put(categories.get(index), values[index]);
        }
        if (!categories.isEmpty()) {
            fireTableRowsUpdated(0, getRowCount() - 1);
        }
    }

    Object[] copyLimitValues() {
        List<Object> values = new ArrayList<>(categories.size());
        for (Category category : categories) {
            values.add(limitValues.get(category));
        }
        return values.toArray();
    }

    @Override
    public int getRowCount() {
        return categories.size();
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
            case 2 -> limitValues.get(category);
            case 3 -> usage.getRemaining().<Object>map(value -> value).orElse("Not set");
            case 4 -> usage.getAlertLevel();
            default -> throw new IndexOutOfBoundsException(
                    "Budget column index is out of range: " + columnIndex);
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == LIMIT_COLUMN
                && getCategoryAt(rowIndex).isActive();
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        Category category = getCategoryAt(rowIndex);
        if (columnIndex != LIMIT_COLUMN || category.isArchived()) {
            return;
        }
        limitValues.put(category, value == null ? "" : value);
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    private static BudgetUsage emptyUsage() {
        return new BudgetUsage(new BigDecimal("0.00"), Optional.empty());
    }
}
