package com.wealthora.server.api;

public record AdminSecurityResponse(
        String passwordPolicy,
        String accessTokenExpiry,
        String refreshTokenExpiry,
        String lockDuration,
        int maximumFailedLoginAttempts,
        String verificationExpiry,
        int maximumVerificationAttempts,
        String passwordResetExpiry) {
}
