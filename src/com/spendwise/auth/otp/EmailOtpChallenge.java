package com.spendwise.auth.otp;

import java.time.Instant;
import java.util.Objects;

public record EmailOtpChallenge(
        String challengeIdentifier,
        String email,
        OtpPurpose purpose,
        Instant expiresAt,
        Instant resendAvailableAt) {

    public EmailOtpChallenge {
        challengeIdentifier = required(
                challengeIdentifier, "Challenge identifier");
        email = required(email, "Email");
        purpose = Objects.requireNonNull(purpose, "OTP purpose is required.");
        expiresAt = Objects.requireNonNull(expiresAt,
                "OTP expiry is required.");
        resendAvailableAt = Objects.requireNonNull(resendAvailableAt,
                "OTP resend time is required.");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.strip();
    }
}
