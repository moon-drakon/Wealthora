package com.spendwise.auth.otp;

import com.spendwise.auth.AuthConfigurationException;

public final class UnconfiguredEmailVerificationGateway
        implements EmailVerificationGateway {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public EmailOtpChallenge sendCode(
            String normalizedEmail, OtpPurpose purpose,
            String existingChallengeIdentifier) {
        throw unavailable();
    }

    @Override
    public void verifyCode(
            String normalizedEmail, OtpPurpose purpose,
            String challengeIdentifier, String code) {
        throw unavailable();
    }

    private static AuthConfigurationException unavailable() {
        return new AuthConfigurationException(
                "Email OTP is not configured. Existing sign-in, finance, and "
                + "offline recovery remain available.");
    }
}
