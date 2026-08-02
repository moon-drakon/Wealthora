package com.spendwise.auth;

import java.time.Instant;
import java.util.Objects;

public final class UserSession {

    public enum Provider {
        NSU_PASSWORD, GOOGLE
    }

    private final String userIdentifier;
    private final String email;
    private final String displayName;
    private final boolean verified;
    private final Provider provider;
    private final Instant authenticatedAt;

    public UserSession(
            String userIdentifier,
            String email,
            String displayName,
            boolean verified,
            Provider provider,
            Instant authenticatedAt) {
        this.userIdentifier = required(userIdentifier, "User ID");
        this.email = NsuEmailPolicy.requireInstitutionalEmail(email);
        this.displayName = required(displayName, "Display name");
        this.verified = verified;
        this.provider = Objects.requireNonNull(
                provider, "Authentication provider is required.");
        this.authenticatedAt = Objects.requireNonNull(
                authenticatedAt, "Authentication time is required.");
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isVerified() {
        return verified;
    }

    public Provider getProvider() {
        return provider;
    }

    public Instant getAuthenticatedAt() {
        return authenticatedAt;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuthException(fieldName + " is required.");
        }
        return value.strip();
    }
}
