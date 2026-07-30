package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.validation.ExpenseValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ExpenseSummary {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    private final int expenseCount;
    private final BigDecimal totalAmount;
    private final BigDecimal averageAmount;
    private final Map<Category, BigDecimal> totalsByCategory;

    ExpenseSummary(
            int expenseCount,
            BigDecimal totalAmount,
            BigDecimal averageAmount,
            Map<Category, BigDecimal> totalsByCategory) {
        this.expenseCount = expenseCount;
        this.totalAmount = normalizeMoney(totalAmount, "Total amount");
        this.averageAmount = normalizeMoney(averageAmount, "Average amount");

        Map<Category, BigDecimal> requiredTotals = Objects.requireNonNull(
                totalsByCategory, "Category totals are required.");
        LinkedHashMap<Category, BigDecimal> copiedTotals = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            BigDecimal categoryTotal = requiredTotals.getOrDefault(category, ZERO_AMOUNT);
            copiedTotals.put(
                    category,
                    normalizeMoney(categoryTotal, "Category total for " + category.name()));
        }
        requiredTotals.entrySet().stream()
                .filter(entry -> !entry.getKey().isBuiltIn())
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> copiedTotals.put(
                        entry.getKey(),
                        normalizeMoney(
                                entry.getValue(),
                                "Category total for " + entry.getKey().name())));
        this.totalsByCategory = Collections.unmodifiableMap(copiedTotals);
    }

    public int getExpenseCount() {
        return expenseCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getAverageAmount() {
        return averageAmount;
    }

    public Map<Category, BigDecimal> getTotalsByCategory() {
        return totalsByCategory;
    }

    public BigDecimal getTotalForCategory(Category category) {
        return totalsByCategory.getOrDefault(
                ExpenseValidator.validateCategory(category), ZERO_AMOUNT);
    }

    private static BigDecimal normalizeMoney(BigDecimal amount, String fieldName) {
        return Objects.requireNonNull(amount, fieldName + " is required.")
                .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
