package com.spendwise.ui;

import com.spendwise.model.Category;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class CategoryTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {
        "Name", "Parent", "Type", "Status"
    };
    private List<Category> categories = List.of();

    void replaceCategories(List<Category> newCategories) {
        Objects.requireNonNull(newCategories, "Category list is required.");
        for (Category category : newCategories) {
            Objects.requireNonNull(
                    category, "Category list cannot contain null elements.");
        }
        categories = List.copyOf(newCategories);
        fireTableDataChanged();
    }

    Category getCategoryAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= categories.size()) {
            throw new IndexOutOfBoundsException(
                    "Category row index is out of range: " + rowIndex);
        }
        return categories.get(rowIndex);
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
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Category category = getCategoryAt(rowIndex);
        return switch (columnIndex) {
            case 0 -> category.getDisplayName();
            case 1 -> category.getParentIdentifier().orElse("—");
            case 2 -> category.isBuiltIn() ? "Built-in"
                    : category.isSubcategory() ? "Subcategory" : "Custom";
            case 3 -> category.isActive() ? "Active" : "Archived";
            default -> throw new IndexOutOfBoundsException(
                    "Category column index is out of range: " + columnIndex);
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
