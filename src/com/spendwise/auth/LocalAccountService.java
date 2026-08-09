package com.spendwise.auth;

/** Local-only registration and password recovery capabilities. */
public interface LocalAccountService {

    PasswordRecoveryChallenge getPasswordRecoveryChallenge(String email);

    void resetPasswordWithRecovery(
            String email,
            char[] recoveryAnswer,
            char[] newPassword,
            char[] passwordConfirmation);

    boolean hasPasswordRecovery(UserSession session);

    void updatePasswordRecovery(
            UserSession session,
            char[] currentPassword,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer,
            char[] recoveryAnswerConfirmation);
}
