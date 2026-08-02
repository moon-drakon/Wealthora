package com.spendwise.auth;

import java.time.Instant;
import java.util.Objects;

public final class UserSession {

    private final AuthenticatedUser user;
    private final Instant authenticatedAt;

    public UserSession(AuthenticatedUser user, Instant authenticatedAt) {
        this.user = Objects.requireNonNull(user, "Authenticated user is required.");
        if (!user.isEmailVerified()
                || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AuthException(
                    "Only an active account with a verified email can start a session.");
        }
        this.authenticatedAt = Objects.requireNonNull(
                authenticatedAt, "Authentication time is required.");
    }

    public String getUserIdentifier() {
        return user.getUserIdentifier();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getDisplayName() {
        return user.getFullName();
    }

    public boolean isVerified() {
        return user.isEmailVerified();
    }

    public AuthProvider getProvider() {
        return user.getPrimaryAuthProvider();
    }

    public String getGoogleSubjectId() {
        return user.getGoogleSubjectId();
    }

    public AuthenticatedUser getUser() {
        return user;
    }

    public Instant getAuthenticatedAt() {
        return authenticatedAt;
    }

    public boolean hasRole(UserRole role) {
        return user.hasRole(role);
    }

    public boolean canAccessAdminConsole() {
        return hasRole(UserRole.ADMIN) || hasRole(UserRole.OWNER);
    }

    public boolean isOwner() {
        return hasRole(UserRole.OWNER);
    }

}
