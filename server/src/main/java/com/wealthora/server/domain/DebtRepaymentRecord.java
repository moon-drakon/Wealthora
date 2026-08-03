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
@Table(name = "debt_repayments")
public class DebtRepaymentRecord {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "debt_id", nullable = false) private UUID debtId;
    @Column(name = "repayment_date", nullable = false) private LocalDate date;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 500) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DebtRepaymentRecord() {
    }

    public DebtRepaymentRecord(
            UUID id, UUID userId, String externalId, UUID debtId,
            LocalDate date, BigDecimal amount, String note, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.debtId = debtId;
        this.date = date;
        this.amount = amount;
        this.note = note;
        this.createdAt = now;
    }

    public String getExternalId() { return externalId; }
    public UUID getUserId() { return userId; }
    public UUID getDebtId() { return debtId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
}
