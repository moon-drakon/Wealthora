package com.wealthora.server.api;

import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import java.time.Instant;
import java.util.Set;

public record UserResponse(
        String userIdentifier,
        String fullName,
        String email,
        String studentId,
        boolean emailVerified,
        String accountStatus,
        Set<String> roles,
        String primaryAuthProvider,
        String googleSubjectId,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {

    public static UserResponse from(UserAccount user, Set<String> roles) {
        return new UserResponse(user.getId().toString(), user.getFullName(),
                user.getEmail(), user.getStudentId(), user.isEmailVerified(),
                user.getAccountStatus().name(), roles, "LOCAL", "",
                user.getCreatedAt(), user.getUpdatedAt(),
                user.getLastLoginAt());
    }

    public static UserResponse from(
            UserAccount user, Set<String> roles,
            java.util.List<AuthenticationIdentity> identities) {
        AuthenticationIdentity google = identities.stream()
                .filter(identity -> identity.getProvider() == AuthProvider.GOOGLE)
                .findFirst().orElse(null);
        boolean password = identities.stream().anyMatch(
                identity -> identity.getProvider() == AuthProvider.PASSWORD);
        String provider = google == null ? "LOCAL"
                : password ? "LOCAL_AND_GOOGLE" : "GOOGLE";
        return new UserResponse(user.getId().toString(), user.getFullName(),
                user.getEmail(), user.getStudentId(), user.isEmailVerified(),
                user.getAccountStatus().name(), roles, provider,
                google == null ? "" : google.getProviderSubject(),
                user.getCreatedAt(), user.getUpdatedAt(),
                user.getLastLoginAt());
    }
}
