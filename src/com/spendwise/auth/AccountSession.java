package com.spendwise.auth;

import java.time.Instant;

public record AccountSession(
        String sessionIdentifier,
        String deviceLabel,
        Instant createdAt,
        Instant accessExpiresAt,
        boolean currentSession) {

    public AccountSession {
        if (sessionIdentifier == null || sessionIdentifier.isBlank()) {
            throw new AuthException("Session identifier is required.");
        }
        deviceLabel = deviceLabel == null || deviceLabel.isBlank()
                ? "Wealthora Desktop" : deviceLabel.strip();
        if (createdAt == null) {
            throw new AuthException("Session creation time is required.");
        }
    }
}
