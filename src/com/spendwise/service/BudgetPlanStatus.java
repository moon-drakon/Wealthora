package com.spendwise.service;

import com.spendwise.model.BudgetPlan;
import com.spendwise.model.Category;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BudgetPlanStatus {
    private final BudgetPlan plan;
    private final BudgetUsage overallUsage;
    private final BigDecimal overallRollover;
    private final Map<Category, BudgetUsage> categoryUsage;
    private final Map<Category, BigDecimal> categoryRollover;

    public BudgetPlanStatus(
            BudgetPlan plan, BudgetUsage overallUsage,
            BigDecimal overallRollover,
            Map<Category, BudgetUsage> categoryUsage,
            Map<Category, BigDecimal> categoryRollover) {
        this.plan = Objects.requireNonNull(plan);
        this.overallUsage = Objects.requireNonNull(overallUsage);
        this.overallRollover = Objects.requireNonNull(overallRollover);
        this.categoryUsage = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(categoryUsage)));
        this.categoryRollover = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(categoryRollover)));
    }

    public BudgetPlan getPlan() { return plan; }
    public BudgetUsage getOverallUsage() { return overallUsage; }
    public BigDecimal getOverallRollover() { return overallRollover; }
    public Map<Category, BudgetUsage> getCategoryUsage() { return categoryUsage; }
    public Map<Category, BigDecimal> getCategoryRollover() {
        return categoryRollover;
    }

    public BudgetAlertLevel getHighestAlertLevel() {
        BudgetAlertLevel result = overallUsage.getAlertLevel();
        for (BudgetUsage usage : categoryUsage.values()) {
            if (severity(usage.getAlertLevel()) > severity(result)) {
                result = usage.getAlertLevel();
            }
        }
        return result;
    }

    private static int severity(BudgetAlertLevel level) {
        return switch (level) {
            case NOT_SET -> 0;
            case WITHIN_LIMIT -> 1;
            case NEAR_LIMIT -> 2;
            case LIMIT_REACHED -> 3;
            case OVER_LIMIT -> 4;
        };
    }
}
