package com.spendwise.auth;

/** Local-only registration and password recovery capabilities. */
public interface LocalAccountService {

    AuthenticatedUser registerLocalAccount(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            char[] passwordConfirmation,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer);

    PasswordRecoveryChallenge getPasswordRecoveryChallenge(String email);

    void resetPasswordWithRecovery(
            String email,
            char[] recoveryAnswer,
            char[] newPassword,
            char[] passwordConfirmation);
}
