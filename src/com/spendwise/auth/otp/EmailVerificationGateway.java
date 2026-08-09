package com.spendwise.auth.otp;

public interface EmailVerificationGateway {

    boolean isConfigured();

    EmailOtpChallenge sendCode(
            String normalizedEmail,
            OtpPurpose purpose,
            String existingChallengeIdentifier);

    void verifyCode(
            String normalizedEmail,
            OtpPurpose purpose,
            String challengeIdentifier,
            String code);
}
