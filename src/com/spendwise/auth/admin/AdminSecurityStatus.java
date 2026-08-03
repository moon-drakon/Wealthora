package com.spendwise.auth.admin;

public record AdminSecurityStatus(
        String passwordPolicy,
        String accessTokenExpiry,
        String refreshTokenExpiry,
        String lockDuration,
        int maximumFailedLoginAttempts,
        String verificationExpiry,
        int maximumVerificationAttempts,
        String passwordResetExpiry) {
}
