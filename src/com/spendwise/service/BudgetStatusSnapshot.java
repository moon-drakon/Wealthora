package com.spendwise.service;

import com.spendwise.model.Category;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BudgetStatusSnapshot {

    private final YearMonth selectedMonth;
    private final BudgetUsage overallUsage;
    private final Map<Category, BudgetUsage> categoryUsage;

    public BudgetStatusSnapshot(
            YearMonth selectedMonth,
            BudgetUsage overallUsage,
            Map<Category, BudgetUsage> categoryUsage) {
        this.selectedMonth = Objects.requireNonNull(
                selectedMonth, "Selected budget month is required.");
        this.overallUsage = Objects.requireNonNull(
                overallUsage, "Overall budget usage is required.");
        this.categoryUsage = copyCategoryUsage(categoryUsage);
    }

    public YearMonth getSelectedMonth() {
        return selectedMonth;
    }

    public BudgetUsage getOverallUsage() {
        return overallUsage;
    }

    public Map<Category, BudgetUsage> getCategoryUsage() {
        return categoryUsage;
    }

    public BudgetUsage getUsageForCategory(Category category) {
        return categoryUsage.get(
                Objects.requireNonNull(category, "Budget category is required."));
    }

    public BudgetAlertLevel getHighestActiveAlertLevel() {
        BudgetAlertLevel highest = overallUsage.getAlertLevel();
        for (BudgetUsage usage : categoryUsage.values()) {
            if (severity(usage.getAlertLevel()) > severity(highest)) {
                highest = usage.getAlertLevel();
            }
        }
        return highest;
    }

    private static Map<Category, BudgetUsage> copyCategoryUsage(
            Map<Category, BudgetUsage> suppliedUsage) {
        Objects.requireNonNull(suppliedUsage, "Category budget usage is required.");
        for (Map.Entry<Category, BudgetUsage> entry : suppliedUsage.entrySet()) {
            Objects.requireNonNull(
                    entry.getKey(), "Category budget usage cannot contain a null category.");
            Objects.requireNonNull(
                    entry.getValue(), "Category budget usage cannot contain a null value.");
        }

        LinkedHashMap<Category, BudgetUsage> copiedUsage = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            BudgetUsage usage = suppliedUsage.get(category);
            if (usage == null) {
                throw new NullPointerException(
                        "Category budget usage is missing " + category.name() + ".");
            }
            copiedUsage.put(category, usage);
        }
        suppliedUsage.entrySet().stream()
                .filter(entry -> !entry.getKey().isBuiltIn())
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> copiedUsage.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(copiedUsage);
    }

    private static int severity(BudgetAlertLevel alertLevel) {
        return switch (alertLevel) {
            case NOT_SET -> 0;
            case WITHIN_LIMIT -> 1;
            case NEAR_LIMIT -> 2;
            case LIMIT_REACHED -> 3;
            case OVER_LIMIT -> 4;
        };
    }
}
