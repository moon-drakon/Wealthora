package com.spendwise.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
    private final Instant lastLoginAt;
    private final Set<UserRole> roles;
    private final String studentIdentifier;
    private final String preferredTheme;
    private final String preferredCurrency;

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
        this(userIdentifier, fullName, email, emailVerified,
                primaryAuthProvider, googleSubjectId, accountStatus,
                createdAt, updatedAt, null, Set.of(UserRole.USER), "",
                "System", "BDT");
    }

    public AuthenticatedUser(
            String userIdentifier,
            String fullName,
            String email,
            boolean emailVerified,
            AuthProvider primaryAuthProvider,
            String googleSubjectId,
            AccountStatus accountStatus,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            Set<UserRole> roles,
            String studentIdentifier,
            String preferredTheme,
            String preferredCurrency) {
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
        this.lastLoginAt = lastLoginAt;
        this.roles = validatedRoles(roles);
        this.studentIdentifier = optional(studentIdentifier);
        this.preferredTheme = required(preferredTheme, "Theme preference");
        this.preferredCurrency = required(
                preferredCurrency, "Currency preference").toUpperCase(Locale.ROOT);
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

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(Objects.requireNonNull(role));
    }

    public UserRole getHighestRole() {
        if (hasRole(UserRole.OWNER)) return UserRole.OWNER;
        if (hasRole(UserRole.ADMIN)) return UserRole.ADMIN;
        return UserRole.USER;
    }

    public String getStudentIdentifier() {
        return studentIdentifier;
    }

    public String getPreferredTheme() {
        return preferredTheme;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public AuthenticatedUser withLastLogin(Instant value) {
        Instant required = Objects.requireNonNull(
                value, "Last-login time is required.");
        return copy(accountStatus, roles, required, required);
    }

    public AuthenticatedUser withRoles(Set<UserRole> value, Instant changedAt) {
        return copy(accountStatus, value, lastLoginAt, changedAt);
    }

    public AuthenticatedUser withStatus(
            AccountStatus value, Instant changedAt) {
        return copy(value, roles, lastLoginAt, changedAt);
    }

    public boolean isNsuEmail() {
        return NsuEmailPolicy.isInstitutionalEmail(email);
    }

    public List<String> getProfileBadges() {
        List<String> badges = new ArrayList<>();
        if (primaryAuthProvider == AuthProvider.LOCAL) {
            if (emailVerified && isNsuEmail()) {
                badges.add("NSU Password Account");
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

    private AuthenticatedUser copy(
            AccountStatus status,
            Set<UserRole> assignedRoles,
            Instant lastLogin,
            Instant changedAt) {
        return new AuthenticatedUser(userIdentifier, fullName, email,
                emailVerified, primaryAuthProvider, googleSubjectId, status,
                createdAt, changedAt, lastLogin, assignedRoles,
                studentIdentifier, preferredTheme, preferredCurrency);
    }

    private static Set<UserRole> validatedRoles(Set<UserRole> values) {
        if (values == null || values.isEmpty()) {
            throw new AuthException("At least the USER role is required.");
        }
        EnumSet<UserRole> copy = EnumSet.copyOf(values);
        if (!copy.contains(UserRole.USER)) {
            throw new AuthException("Every account must have the USER role.");
        }
        if (copy.contains(UserRole.OWNER)
                && !copy.contains(UserRole.ADMIN)) {
            throw new AuthException(
                    "The OWNER role must include ADMIN capability.");
        }
        return Set.copyOf(copy);
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
