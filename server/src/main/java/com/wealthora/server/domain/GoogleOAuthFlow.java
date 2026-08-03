package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "google_oauth_flows")
public class GoogleOAuthFlow {

    @Id private UUID id;
    @Column(name = "poll_secret_hash", nullable = false, unique = true,
            length = 64)
    private String pollSecretHash;
    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;
    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;
    @Column(name = "device_label", nullable = false, length = 160)
    private String deviceLabel;
    @Enumerated(EnumType.STRING)
    @Column(name = "flow_status", nullable = false, length = 20)
    private GoogleOAuthFlowStatus status;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "failure_message", length = 300)
    private String failureMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "consumed_at") private Instant consumedAt;

    protected GoogleOAuthFlow() {
    }

    public GoogleOAuthFlow(
            UUID id, String pollSecretHash, String stateHash,
            String nonceHash, String deviceLabel,
            Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.pollSecretHash = pollSecretHash;
        this.stateHash = stateHash;
        this.nonceHash = nonceHash;
        this.deviceLabel = deviceLabel;
        this.status = GoogleOAuthFlowStatus.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getPollSecretHash() { return pollSecretHash; }
    public String getStateHash() { return stateHash; }
    public String getNonceHash() { return nonceHash; }
    public String getDeviceLabel() { return deviceLabel; }
    public GoogleOAuthFlowStatus getStatus() { return status; }
    public UUID getUserId() { return userId; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isPendingAt(Instant now) {
        return status == GoogleOAuthFlowStatus.PENDING
                && expiresAt.isAfter(now);
    }

    public void complete(UUID completedUserId, Instant now) {
        status = GoogleOAuthFlowStatus.COMPLETED;
        userId = completedUserId;
        completedAt = now;
    }

    public void fail(String message, Instant now) {
        status = GoogleOAuthFlowStatus.FAILED;
        failureMessage = message;
        completedAt = now;
    }

    public void consume(Instant now) {
        status = GoogleOAuthFlowStatus.CONSUMED;
        consumedAt = now;
    }
}
