package com.spendwise.auth;

public interface AuthService {

    UserSession signInWithNsuEmail(String email, char[] password);

    UserSession continueWithGoogle();

    AuthenticatedUser registerWithNsuEmail(
            String fullName, String email, char[] password);

    default AuthenticatedUser registerWithNsuEmail(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            boolean termsAccepted) {
        if (!termsAccepted) {
            throw new AuthException(
                    "Accept the terms and privacy notice to create an account.");
        }
        return registerWithNsuEmail(fullName, email, password);
    }

    AuthenticatedUser verifyNsuEmail(
            String email, String verificationCode);

    void resendVerification(String email);

    void forgotPassword(String email);

    void resetPassword(String email, String resetToken, char[] newPassword);

    UserSession refreshSession();

    void logout();

    AuthenticatedUser getCurrentUser();
}
