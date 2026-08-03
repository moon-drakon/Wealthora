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
@Table(name = "savings_goals")
public class SavingsGoalRecord {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;
    @Column(name = "target_date", nullable = false) private LocalDate targetDate;
    @Column(name = "linked_account_id", nullable = false)
    private UUID linkedAccountId;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SavingsGoalRecord() {
    }

    public SavingsGoalRecord(
            UUID id, UUID userId, String externalId, String name,
            BigDecimal targetAmount, LocalDate targetDate,
            UUID linkedAccountId, boolean active, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.linkedAccountId = linkedAccountId;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public LocalDate getTargetDate() { return targetDate; }
    public UUID getLinkedAccountId() { return linkedAccountId; }
    public boolean isActive() { return active; }

    public void update(
            String name, BigDecimal targetAmount, LocalDate targetDate,
            UUID linkedAccountId, boolean active, Instant now) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.linkedAccountId = linkedAccountId;
        this.active = active;
        this.updatedAt = now;
    }
}
