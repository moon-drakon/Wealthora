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
@Table(name = "transfers")
public class FinanceTransfer {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "source_account_id", nullable = false) private UUID sourceId;
    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(name = "occurred_on", nullable = false) private LocalDate occurredOn;
    @Column(nullable = false, length = 1000) private String tags;
    @Column(nullable = false, length = 500) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FinanceTransfer() {
    }

    public FinanceTransfer(
            UUID id, UUID userId, String externalId, UUID sourceId,
            UUID destinationId, BigDecimal amount, LocalDate occurredOn,
            String tags, String note, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.tags = tags;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public UUID getSourceId() { return sourceId; }
    public UUID getDestinationId() { return destinationId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getTags() { return tags; }
    public String getNote() { return note; }

    public void update(
            UUID sourceId, UUID destinationId, BigDecimal amount,
            LocalDate occurredOn, String tags, String note, Instant now) {
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.tags = tags;
        this.note = note;
        this.updatedAt = now;
    }
}
