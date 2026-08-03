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
@Table(name = "authentication_identities")
public class AuthenticationIdentity {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;
    @Column(name = "provider_subject")
    private String providerSubject;
    @Column(name = "password_hash", length = 100)
    private String passwordHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthenticationIdentity() {
    }

    public AuthenticationIdentity(
            UUID id, UUID userId, AuthProvider provider,
            String passwordHash, Instant now) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getPasswordHash() { return passwordHash; }
}
