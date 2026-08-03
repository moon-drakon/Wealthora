package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finance_preferences")
public class FinancePreference {

    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "default_account_id", nullable = false)
    private UUID defaultAccountId;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FinancePreference() {
    }

    public FinancePreference(UUID userId, UUID defaultAccountId, Instant now) {
        this.userId = userId;
        this.defaultAccountId = defaultAccountId;
        this.updatedAt = now;
    }

    public UUID getDefaultAccountId() { return defaultAccountId; }

    public void setDefaultAccountId(UUID identifier, Instant now) {
        defaultAccountId = identifier;
        updatedAt = now;
    }
}
