package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budget_plans")
public class BudgetPlanRecord {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "overall_limit", precision = 19, scale = 4)
    private BigDecimal overallLimit;
    @Column(name = "category_limits", nullable = false, length = 8000)
    private String categoryLimits;
    @Column(name = "rollover_mode", nullable = false, length = 40)
    private String rolloverMode;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected BudgetPlanRecord() {
    }

    public BudgetPlanRecord(
            UUID id, UUID userId, String externalId, String name,
            LocalDate startDate, LocalDate endDate, BigDecimal overallLimit,
            String categoryLimits, String rolloverMode, boolean active,
            Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.overallLimit = overallLimit;
        this.categoryLimits = categoryLimits;
        this.rolloverMode = rolloverMode;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getOverallLimit() { return overallLimit; }
    public String getCategoryLimits() { return categoryLimits; }
    public String getRolloverMode() { return rolloverMode; }
    public boolean isActive() { return active; }

    public void update(
            String name, LocalDate startDate, LocalDate endDate,
            BigDecimal overallLimit, String categoryLimits,
            String rolloverMode, boolean active, Instant now) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.overallLimit = overallLimit;
        this.categoryLimits = categoryLimits;
        this.rolloverMode = rolloverMode;
        this.active = active;
        this.updatedAt = now;
    }
}
