package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "attempted_email_hash", nullable = false, length = 64)
    private String attemptedEmailHash;
    @Column(nullable = false)
    private boolean successful;
    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;
    @Column(name = "remote_address_hash", length = 64)
    private String remoteAddressHash;

    protected LoginAttempt() {
    }

    public LoginAttempt(
            UUID id, UUID userId, String attemptedEmailHash,
            boolean successful, Instant attemptedAt,
            String remoteAddressHash) {
        this.id = id;
        this.userId = userId;
        this.attemptedEmailHash = attemptedEmailHash;
        this.successful = successful;
        this.attemptedAt = attemptedAt;
        this.remoteAddressHash = remoteAddressHash;
    }
}
