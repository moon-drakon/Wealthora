package com.spendwise.service;

import com.spendwise.model.Category;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BudgetActualSummary {

    private final YearMonth month;
    private final Optional<BigDecimal> overallLimit;
    private final BigDecimal actualExpenses;
    private final Map<Category, BigDecimal> categoryLimits;
    private final Map<Category, BigDecimal> categoryActuals;

    public BudgetActualSummary(
            YearMonth month,
            Optional<BigDecimal> overallLimit,
            BigDecimal actualExpenses,
            Map<Category, BigDecimal> categoryLimits,
            Map<Category, BigDecimal> categoryActuals) {
        this.month = Objects.requireNonNull(month, "Budget month is required.");
        this.overallLimit = Objects.requireNonNull(
                overallLimit, "Overall budget limit is required.")
                .map(value -> money(value, "Overall budget limit"));
        this.actualExpenses = money(actualExpenses, "Actual expenses");
        this.categoryLimits = copyMoneyMap(categoryLimits, "Category limits");
        this.categoryActuals = copyMoneyMap(categoryActuals, "Category actuals");
    }

    public YearMonth getMonth() {
        return month;
    }

    public Optional<BigDecimal> getOverallLimit() {
        return overallLimit;
    }

    public BigDecimal getActualExpenses() {
        return actualExpenses;
    }

    public Map<Category, BigDecimal> getCategoryLimits() {
        return categoryLimits;
    }

    public Map<Category, BigDecimal> getCategoryActuals() {
        return categoryActuals;
    }

    private static Map<Category, BigDecimal> copyMoneyMap(
            Map<Category, BigDecimal> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " are required.");
        LinkedHashMap<Category, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<Category, BigDecimal> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(),
                            fieldName + " cannot contain a null category."),
                    money(entry.getValue(), fieldName + " value"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static BigDecimal money(BigDecimal value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
