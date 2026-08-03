package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntry {

    @Id
    private UUID id;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "actor_user_id")
    private UUID actorUserId;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(name = "target_user_id")
    private UUID targetUserId;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Column(length = 500)
    private String reason;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(
            UUID id, Instant occurredAt, UUID actorUserId, String action,
            UUID targetUserId, String outcome, String reason) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.outcome = outcome;
        this.reason = reason;
    }
}
