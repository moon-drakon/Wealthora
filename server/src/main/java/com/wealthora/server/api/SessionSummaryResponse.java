package com.wealthora.server.api;

import java.time.Instant;

public record SessionSummaryResponse(
        String sessionIdentifier,
        String deviceLabel,
        Instant createdAt,
        Instant accessExpiresAt,
        boolean currentSession) {
}
