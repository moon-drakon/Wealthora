package com.wealthora.server.api;

import java.time.Instant;

public record GoogleOAuthStartResponse(
        String flowIdentifier,
        String pollSecret,
        String authorizationUrl,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "GoogleOAuthStartResponse[flowIdentifier=" + flowIdentifier
                + ", pollSecret=[REDACTED], authorizationUrl=[REDACTED], expiresAt="
                + expiresAt + "]";
    }
}
