package com.spendwise.auth;

public interface AuthApiClient {

    UserSession signIn(String email, char[] password);

    UserSession signInWithGoogle();

    UserSession createAccount(
            String displayName, String email, char[] password);

    UserSession verifyEmail(String email, String verificationCode);

    void requestPasswordReset(String email);

    void resetPassword(String resetToken, char[] newPassword);
}
