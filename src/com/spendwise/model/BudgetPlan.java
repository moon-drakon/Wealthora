package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BudgetPlan {

    private static final String ID_PREFIX = "BUDGET_";

    private final String identifier;
    private final String name;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal overallLimit;
    private final Map<Category, BigDecimal> categoryLimits;
    private final BudgetRolloverMode rolloverMode;
    private final boolean active;

    public static BudgetPlan create(
            String name, LocalDate startDate, LocalDate endDate,
            BigDecimal overallLimit, Map<Category, BigDecimal> categoryLimits,
            BudgetRolloverMode rolloverMode) {
        return new BudgetPlan(
                ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                        .toUpperCase(Locale.ROOT),
                name, startDate, endDate, overallLimit, categoryLimits,
                rolloverMode, true);
    }

    public BudgetPlan(
            String identifier, String name, LocalDate startDate,
            LocalDate endDate, BigDecimal overallLimit,
            Map<Category, BigDecimal> categoryLimits,
            BudgetRolloverMode rolloverMode, boolean active) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Budget plan", ID_PREFIX);
        this.name = FinanceValidator.validateRequiredText(
                name, "Budget name", FinanceValidator.MAX_NAME_LENGTH);
        this.startDate = Objects.requireNonNull(
                startDate, "Budget start date is required.");
        this.endDate = Objects.requireNonNull(
                endDate, "Budget end date is required.");
        if (endDate.isBefore(startDate)) {
            throw new ValidationException(
                    "Budget end date must not be before its start date.");
        }
        this.overallLimit = overallLimit == null ? null
                : FinanceValidator.validatePositiveAmount(
                        overallLimit, "Overall budget limit");
        this.categoryLimits = copyLimits(categoryLimits);
        if (this.overallLimit == null && this.categoryLimits.isEmpty()) {
            throw new ValidationException(
                    "A budget plan requires an overall or category limit.");
        }
        this.rolloverMode = Objects.requireNonNull(
                rolloverMode, "Budget rollover mode is required.");
        this.active = active;
    }

    public String getIdentifier() { return identifier; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Optional<BigDecimal> getOverallLimit() {
        return Optional.ofNullable(overallLimit);
    }
    public Map<Category, BigDecimal> getCategoryLimits() {
        return categoryLimits;
    }
    public Optional<BigDecimal> getCategoryLimit(Category category) {
        return Optional.ofNullable(categoryLimits.get(
                Objects.requireNonNull(category)));
    }
    public BudgetRolloverMode getRolloverMode() { return rolloverMode; }
    public boolean isActive() { return active; }

    public boolean includes(LocalDate date) {
        LocalDate required = Objects.requireNonNull(date);
        return !required.isBefore(startDate) && !required.isAfter(endDate);
    }

    public BudgetPlan withActive(boolean newActive) {
        return new BudgetPlan(identifier, name, startDate, endDate,
                overallLimit, categoryLimits, rolloverMode, newActive);
    }

    private static Map<Category, BigDecimal> copyLimits(
            Map<Category, BigDecimal> supplied) {
        Objects.requireNonNull(supplied, "Category limits are required.");
        LinkedHashMap<Category, BigDecimal> result = new LinkedHashMap<>();
        supplied.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(
                        Objects.requireNonNull(entry.getKey(),
                                "A budget category is required."),
                        FinanceValidator.validatePositiveAmount(
                                entry.getValue(),
                                entry.getKey().getDisplayName() + " limit")));
        return Collections.unmodifiableMap(result);
    }
}
