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
@Table(name = "transactions")
public class FinanceTransaction {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "transaction_type", nullable = false, length = 40)
    private String transactionType;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 160) private String description;
    @Column(name = "occurred_on", nullable = false) private LocalDate occurredOn;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod;
    @Column(nullable = false, length = 1000) private String tags;
    @Column(nullable = false, length = 500) private String note;
    @Column(name = "transfer_id") private UUID transferId;
    @Column(name = "transfer_direction", length = 20)
    private String transferDirection;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FinanceTransaction() {
    }

    public FinanceTransaction(
            UUID id, UUID userId, String externalId, UUID accountId,
            UUID categoryId, String transactionType, BigDecimal amount,
            String description, LocalDate occurredOn, String paymentMethod,
            String tags, String note, UUID transferId,
            String transferDirection, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.description = description;
        this.occurredOn = occurredOn;
        this.occurredAt = occurredOn.atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant();
        this.paymentMethod = paymentMethod;
        this.tags = tags;
        this.note = note;
        this.transferId = transferId;
        this.transferDirection = transferDirection;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public UUID getAccountId() { return accountId; }
    public UUID getCategoryId() { return categoryId; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTags() { return tags; }
    public String getNote() { return note; }
    public UUID getTransferId() { return transferId; }
    public String getTransferDirection() { return transferDirection; }

    public void update(
            UUID accountId, UUID categoryId, BigDecimal amount,
            String description, LocalDate occurredOn, String paymentMethod,
            String tags, String note, Instant now) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.description = description;
        this.occurredOn = occurredOn;
        this.occurredAt = occurredOn.atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant();
        this.paymentMethod = paymentMethod;
        this.tags = tags;
        this.note = note;
        this.updatedAt = now;
    }
}
