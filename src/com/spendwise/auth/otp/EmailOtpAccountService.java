package com.spendwise.auth.otp;

import com.spendwise.auth.AuthenticatedUser;

public interface EmailOtpAccountService {

    boolean isEmailOtpConfigured();

    EmailOtpChallenge beginRegistration(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            char[] passwordConfirmation,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer);

    EmailOtpChallenge resendRegistration(String challengeIdentifier);

    AuthenticatedUser verifyRegistration(
            String challengeIdentifier, String code);

    void cancelRegistration(String challengeIdentifier);

    EmailOtpChallenge beginPasswordReset(String email);

    EmailOtpChallenge resendPasswordReset(String challengeIdentifier);

    void completePasswordReset(
            String challengeIdentifier,
            String code,
            char[] newPassword,
            char[] passwordConfirmation);

    void cancelPasswordReset(String challengeIdentifier);
}
