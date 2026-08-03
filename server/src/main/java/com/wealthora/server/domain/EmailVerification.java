package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    protected EmailVerification() {
    }

    public EmailVerification(
            UUID id, UUID userId, String tokenHash,
            Instant sentAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getSentAt() { return sentAt; }
    public int getFailedAttempts() { return failedAttempts; }

    public void recordFailure() { failedAttempts++; }
    public void consume(Instant now) { consumedAt = now; }
}
