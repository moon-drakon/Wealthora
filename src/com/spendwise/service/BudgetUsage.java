package com.spendwise.service;

import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

public final class BudgetUsage {

    private static final int MONEY_SCALE = 2;
    private static final int PERCENTAGE_SCALE = 2;

    private final BigDecimal spent;
    private final Optional<BigDecimal> limit;
    private final Optional<BigDecimal> remaining;
    private final Optional<BigDecimal> usagePercentage;
    private final BudgetAlertLevel alertLevel;

    public BudgetUsage(BigDecimal spent, Optional<BigDecimal> limit) {
        this.spent = normalizeSpent(spent);
        Optional<BigDecimal> requiredLimit = Objects.requireNonNull(
                limit, "Budget-limit Optional is required.");
        if (requiredLimit.isEmpty()) {
            this.limit = Optional.empty();
            this.remaining = Optional.empty();
            this.usagePercentage = Optional.empty();
            this.alertLevel = BudgetAlertLevel.NOT_SET;
            return;
        }

        BigDecimal normalizedLimit = normalizeLimit(requiredLimit.get());
        this.limit = Optional.of(normalizedLimit);
        this.remaining = Optional.of(
                normalizedLimit.subtract(this.spent)
                        .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        this.usagePercentage = Optional.of(
                this.spent
                        .multiply(new BigDecimal("100"))
                        .divide(normalizedLimit, PERCENTAGE_SCALE, RoundingMode.HALF_UP));
        this.alertLevel = classify(this.spent, normalizedLimit);
    }

    public BigDecimal getSpent() {
        return spent;
    }

    public Optional<BigDecimal> getLimit() {
        return limit;
    }

    public Optional<BigDecimal> getRemaining() {
        return remaining;
    }

    public Optional<BigDecimal> getUsagePercentage() {
        return usagePercentage;
    }

    public BudgetAlertLevel getAlertLevel() {
        return alertLevel;
    }

    private static BudgetAlertLevel classify(BigDecimal spent, BigDecimal limit) {
        int limitComparison = spent.compareTo(limit);
        if (limitComparison > 0) {
            return BudgetAlertLevel.OVER_LIMIT;
        }
        if (limitComparison == 0) {
            return BudgetAlertLevel.LIMIT_REACHED;
        }
        return spent.multiply(new BigDecimal("5"))
                .compareTo(limit.multiply(new BigDecimal("4"))) >= 0
                ? BudgetAlertLevel.NEAR_LIMIT
                : BudgetAlertLevel.WITHIN_LIMIT;
    }

    private static BigDecimal normalizeSpent(BigDecimal amount) {
        Objects.requireNonNull(amount, "Spent amount is required.");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Spent amount cannot be negative.");
        }
        return normalizeMoney(amount, "Spent amount");
    }

    private static BigDecimal normalizeLimit(BigDecimal amount) {
        Objects.requireNonNull(amount, "Budget limit is required.");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Budget limit must be greater than zero.");
        }
        return normalizeMoney(amount, "Budget limit");
    }

    private static BigDecimal normalizeMoney(BigDecimal amount, String fieldName) {
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new ValidationException(
                    fieldName + " must have no more than two decimal places.");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
