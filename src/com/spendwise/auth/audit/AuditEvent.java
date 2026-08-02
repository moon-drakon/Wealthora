package com.spendwise.auth.audit;

import java.time.Instant;
import java.util.Objects;

public record AuditEvent(
        Instant occurredAt,
        String actorUserIdentifier,
        AuditAction action,
        String targetUserIdentifier,
        String outcome,
        String reason) {

    public AuditEvent {
        Objects.requireNonNull(occurredAt, "Audit time is required.");
        actorUserIdentifier = safe(actorUserIdentifier);
        Objects.requireNonNull(action, "Audit action is required.");
        targetUserIdentifier = safe(targetUserIdentifier);
        outcome = safe(outcome);
        reason = safe(reason);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
