package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MonthlyBudget {

    private static final int MONEY_SCALE = 2;

    private final YearMonth month;
    private final Optional<BigDecimal> overallLimit;
    private final Map<Category, BigDecimal> categoryLimits;

    public MonthlyBudget(
            YearMonth month,
            Optional<BigDecimal> overallLimit,
            Map<Category, BigDecimal> categoryLimits) {
        this.month = Objects.requireNonNull(month, "Budget month is required.");
        Optional<BigDecimal> requiredOverall = Objects.requireNonNull(
                overallLimit, "Overall-limit Optional is required.");
        this.overallLimit = requiredOverall.map(
                limit -> normalizeLimit(limit, "Overall limit"));
        this.categoryLimits = copyCategoryLimits(categoryLimits);
    }

    public static MonthlyBudget empty(YearMonth month) {
        return new MonthlyBudget(month, Optional.empty(), Map.of());
    }

    public YearMonth getMonth() {
        return month;
    }

    public Optional<BigDecimal> getOverallLimit() {
        return overallLimit;
    }

    public Map<Category, BigDecimal> getCategoryLimits() {
        return categoryLimits;
    }

    public Optional<BigDecimal> getCategoryLimit(Category category) {
        Objects.requireNonNull(category, "Budget category is required.");
        return Optional.ofNullable(categoryLimits.get(category));
    }

    public boolean hasAnyLimit() {
        return overallLimit.isPresent() || !categoryLimits.isEmpty();
    }

    private static Map<Category, BigDecimal> copyCategoryLimits(
            Map<Category, BigDecimal> suppliedLimits) {
        Objects.requireNonNull(suppliedLimits, "Category limits are required.");
        for (Map.Entry<Category, BigDecimal> entry : suppliedLimits.entrySet()) {
            Objects.requireNonNull(
                    entry.getKey(), "Category limits cannot contain a null category.");
            Objects.requireNonNull(
                    entry.getValue(), "Category limits cannot contain a null amount.");
        }

        EnumMap<Category, BigDecimal> copiedLimits = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            if (suppliedLimits.containsKey(category)) {
                copiedLimits.put(
                        category,
                        normalizeLimit(
                                suppliedLimits.get(category),
                                category.getDisplayName() + " limit"));
            }
        }
        return Collections.unmodifiableMap(copiedLimits);
    }

    private static BigDecimal normalizeLimit(BigDecimal limit, String fieldName) {
        Objects.requireNonNull(limit, fieldName + " is required.");
        if (limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(fieldName + " must be greater than zero.");
        }
        if (limit.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new ValidationException(
                    fieldName + " must have no more than two decimal places.");
        }
        return limit.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
