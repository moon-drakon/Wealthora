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
@Table(name = "debts")
public class DebtRecordEntity {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 40) private String direction;
    @Column(nullable = false, length = 160) private String counterparty;
    @Column(name = "original_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false, length = 500) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DebtRecordEntity() {
    }

    public DebtRecordEntity(
            UUID id, UUID userId, String externalId, String direction,
            String counterparty, BigDecimal originalAmount, LocalDate dueDate,
            String note, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.direction = direction;
        this.counterparty = counterparty;
        this.originalAmount = originalAmount;
        this.dueDate = dueDate;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public String getDirection() { return direction; }
    public String getCounterparty() { return counterparty; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public String getNote() { return note; }

    public void update(
            String direction, String counterparty, BigDecimal originalAmount,
            LocalDate dueDate, String note, Instant now) {
        this.direction = direction;
        this.counterparty = counterparty;
        this.originalAmount = originalAmount;
        this.dueDate = dueDate;
        this.note = note;
        this.updatedAt = now;
    }
}
