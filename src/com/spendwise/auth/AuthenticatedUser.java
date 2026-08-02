package com.spendwise.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AuthenticatedUser {

    private final String userIdentifier;
    private final String fullName;
    private final String email;
    private final boolean emailVerified;
    private final AuthProvider primaryAuthProvider;
    private final String googleSubjectId;
    private final AccountStatus accountStatus;
    private final Instant createdAt;
    private final Instant updatedAt;

    public AuthenticatedUser(
            String userIdentifier,
            String fullName,
            String email,
            boolean emailVerified,
            AuthProvider primaryAuthProvider,
            String googleSubjectId,
            AccountStatus accountStatus,
            Instant createdAt,
            Instant updatedAt) {
        this.userIdentifier = required(userIdentifier, "User ID");
        this.fullName = required(fullName, "Full name");
        this.email = EmailAddressPolicy.normalize(email);
        this.emailVerified = emailVerified;
        this.primaryAuthProvider = Objects.requireNonNull(
                primaryAuthProvider, "Authentication provider is required.");
        this.googleSubjectId = optional(googleSubjectId);
        this.accountStatus = Objects.requireNonNull(
                accountStatus, "Account status is required.");
        this.createdAt = Objects.requireNonNull(
                createdAt, "Created time is required.");
        this.updatedAt = Objects.requireNonNull(
                updatedAt, "Updated time is required.");
        validateProviderIdentity();
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public AuthProvider getPrimaryAuthProvider() {
        return primaryAuthProvider;
    }

    public String getGoogleSubjectId() {
        return googleSubjectId;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isNsuEmail() {
        return NsuEmailPolicy.isInstitutionalEmail(email);
    }

    public List<String> getProfileBadges() {
        List<String> badges = new ArrayList<>();
        if (primaryAuthProvider == AuthProvider.LOCAL) {
            if (emailVerified && isNsuEmail()) {
                badges.add("Verified NSU Account");
            }
        } else {
            badges.add("Google Account");
            if (emailVerified && isNsuEmail()) {
                badges.add("Verified NSU Email");
            }
        }
        return List.copyOf(badges);
    }

    private void validateProviderIdentity() {
        boolean googleProvider = primaryAuthProvider == AuthProvider.GOOGLE
                || primaryAuthProvider == AuthProvider.LOCAL_AND_GOOGLE;
        if (primaryAuthProvider == AuthProvider.LOCAL
                || primaryAuthProvider == AuthProvider.LOCAL_AND_GOOGLE) {
            NsuEmailPolicy.requireInstitutionalEmail(email);
        }
        if (googleProvider && googleSubjectId.isEmpty()) {
            throw new AuthException(
                    "A verified Google subject identifier is required.");
        }
        if (!googleProvider && !googleSubjectId.isEmpty()) {
            throw new AuthException(
                    "A local account cannot contain a Google subject identifier.");
        }
        if (googleProvider && !emailVerified) {
            throw new AuthException(
                    "Google authentication requires a verified email address.");
        }
        if (accountStatus == AccountStatus.ACTIVE && !emailVerified) {
            throw new AuthException(
                    "An active account must have a verified email address.");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuthException(fieldName + " is required.");
        }
        return value.strip();
    }

    private static String optional(String value) {
        return value == null ? "" : value.strip();
    }
}
