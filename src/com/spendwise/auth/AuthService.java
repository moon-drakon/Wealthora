package com.spendwise.auth;

public interface AuthService {

    UserSession signInWithNsuEmail(String email, char[] password);

    UserSession continueWithGoogle();

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
