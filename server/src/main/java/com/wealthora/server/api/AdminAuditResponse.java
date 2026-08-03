package com.wealthora.server.api;

import java.time.Instant;

public record AdminAuditResponse(
        Instant occurredAt,
        String actorUserIdentifier,
        String action,
        String targetUserIdentifier,
        String outcome,
        String reason) {
}
