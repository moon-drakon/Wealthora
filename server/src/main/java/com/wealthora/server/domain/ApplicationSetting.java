package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_settings")
public class ApplicationSetting {

    @Id
    @Column(name = "setting_key", length = 120)
    private String key;
    @Column(name = "setting_value", nullable = false, length = 1000)
    private String value;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ApplicationSetting() {
    }

    public ApplicationSetting(
            String key, String value, Instant updatedAt, UUID updatedBy) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }

    public void update(String newValue, Instant now, UUID actor) {
        value = newValue;
        updatedAt = now;
        updatedBy = actor;
    }
}
