package com.wealthora.server.api;

import com.wealthora.server.domain.UserAccount;
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
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {

    public static UserResponse from(UserAccount user, Set<String> roles) {
        return new UserResponse(user.getId().toString(), user.getFullName(),
                user.getEmail(), user.getStudentId(), user.isEmailVerified(),
                user.getAccountStatus().name(), roles, "LOCAL",
                user.getCreatedAt(), user.getUpdatedAt(),
                user.getLastLoginAt());
    }
}
