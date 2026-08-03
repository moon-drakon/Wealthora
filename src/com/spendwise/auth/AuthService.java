package com.spendwise.auth;

import java.util.List;

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

    default void changePassword(
            char[] currentPassword, char[] newPassword) {
        throw new AuthConfigurationException(
                "Password changes require a configured authentication service.");
    }

    default void setPassword(char[] newPassword) {
        throw new AuthConfigurationException(
                "Setting a password requires a configured authentication service.");
    }

    default List<AccountSession> listSessions() {
        throw new AuthConfigurationException(
                "Session management requires a configured authentication service.");
    }

    default void revokeSession(AccountSession session) {
        throw new AuthConfigurationException(
                "Session management requires a configured authentication service.");
    }

    default void logoutAll() {
        throw new AuthConfigurationException(
                "Session management requires a configured authentication service.");
    }

    UserSession refreshSession();

    void logout();

    AuthenticatedUser getCurrentUser();
}
