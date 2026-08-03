package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionRecord {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "access_token_hash", nullable = false,
            unique = true, length = 64)
    private String accessTokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "device_label", length = 160)
    private String deviceLabel;

    protected SessionRecord() {
    }

    public SessionRecord(
            UUID id, UUID userId, String accessTokenHash,
            Instant createdAt, Instant expiresAt, String deviceLabel) {
        this.id = id;
        this.userId = userId;
        this.accessTokenHash = accessTokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.deviceLabel = deviceLabel;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getDeviceLabel() { return deviceLabel; }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void rotateAccessToken(
            String newAccessTokenHash, Instant newExpiry) {
        accessTokenHash = newAccessTokenHash;
        expiresAt = newExpiry;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
