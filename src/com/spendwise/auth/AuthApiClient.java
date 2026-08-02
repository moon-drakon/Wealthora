package com.spendwise.auth;

/** Future transport boundary for the backend's /api/auth endpoints. */
public interface AuthApiClient {

    UserSession signInWithNsuEmail(String email, char[] password);

    UserSession continueWithGoogle(
            char[] authorizationCode, String redirectUri);

    AuthenticatedUser registerWithNsuEmail(
            String fullName, String email, char[] password);

    AuthenticatedUser verifyNsuEmail(
            String email, String verificationCode);

    void resendVerification(String email);

    void forgotPassword(String email);

    void resetPassword(String email, String resetToken, char[] newPassword);

    UserSession refreshSession();

    void logout();

    AuthenticatedUser getCurrentUser();
}
