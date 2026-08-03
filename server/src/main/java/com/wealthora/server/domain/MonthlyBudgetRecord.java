package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monthly_budgets")
public class MonthlyBudgetRecord {

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "budget_month", nullable = false, length = 7)
    private String budgetMonth;
    @Column(name = "overall_limit", precision = 19, scale = 4)
    private BigDecimal overallLimit;
    @Column(name = "category_limits", nullable = false, length = 8000)
    private String categoryLimits;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MonthlyBudgetRecord() {
    }

    public MonthlyBudgetRecord(
            UUID id, UUID userId, String budgetMonth, BigDecimal overallLimit,
            String categoryLimits, Instant now) {
        this.id = id;
        this.userId = userId;
        this.budgetMonth = budgetMonth;
        this.overallLimit = overallLimit;
        this.categoryLimits = categoryLimits;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getBudgetMonth() { return budgetMonth; }
    public BigDecimal getOverallLimit() { return overallLimit; }
    public String getCategoryLimits() { return categoryLimits; }

    public void update(
            BigDecimal overallLimit, String categoryLimits, Instant now) {
        this.overallLimit = overallLimit;
        this.categoryLimits = categoryLimits;
        this.updatedAt = now;
    }
}
