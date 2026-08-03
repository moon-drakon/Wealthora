package com.wealthora.server.api;

import java.time.Instant;

public record SessionResponse(
        String accessToken,
        String refreshToken,
        Instant authenticatedAt,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserResponse user) {

    @Override
    public String toString() {
        return "SessionResponse[accessToken=[REDACTED], "
                + "refreshToken=[REDACTED], authenticatedAt="
                + authenticatedAt + ", accessTokenExpiresAt="
                + accessTokenExpiresAt + ", refreshTokenExpiresAt="
                + refreshTokenExpiresAt + ", user=" + user + "]";
    }
}
