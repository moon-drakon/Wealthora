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
@Table(name = "goal_contributions")
public class GoalContributionRecord {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "goal_id", nullable = false) private UUID goalId;
    @Column(name = "contribution_date", nullable = false) private LocalDate date;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 500) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected GoalContributionRecord() {
    }

    public GoalContributionRecord(
            UUID id, UUID userId, String externalId, UUID goalId,
            LocalDate date, BigDecimal amount, String note, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.goalId = goalId;
        this.date = date;
        this.amount = amount;
        this.note = note;
        this.createdAt = now;
    }

    public String getExternalId() { return externalId; }
    public UUID getUserId() { return userId; }
    public UUID getGoalId() { return goalId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
}
