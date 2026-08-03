package com.spendwise.auth.registration;

import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.UserSession;
import java.util.List;

public final class UnconfiguredRegistrationGateway
        implements RegistrationGateway {

    @Override
    public AuthenticatedUser register(
            String fullName, String email, String studentIdentifier,
            char[] password, char[] passwordConfirmation,
            boolean termsAccepted) {
        throw unavailable();
    }

    @Override
    public AuthenticatedUser verifyEmail(
            String email, String verificationCode) {
        throw unavailable();
    }

    @Override
    public void resendVerification(String email) {
        throw unavailable();
    }

    @Override
    public void forgotPassword(String email) {
        throw unavailable();
    }

    @Override
    public void resetPassword(
            String email, String resetToken, char[] newPassword) {
        throw unavailable();
    }

    @Override
    public void changePassword(char[] currentPassword, char[] newPassword) {
        throw unavailable();
    }

    @Override
    public void setPassword(char[] newPassword) {
        throw unavailable();
    }

    @Override
    public List<AccountSession> listSessions() {
        throw unavailable();
    }

    @Override
    public void revokeSession(AccountSession session) {
        throw unavailable();
    }

    @Override
    public void logoutAll() {
        throw unavailable();
    }

    @Override
    public UserSession signIn(String email, char[] password) {
        throw unavailable();
    }

    @Override
    public UserSession refreshSession() {
        throw unavailable();
    }

    @Override
    public void logout() {
        throw unavailable();
    }

    @Override
    public AuthenticatedUser getCurrentUser() {
        throw unavailable();
    }

    @Override
    public boolean hasActiveSession() {
        return false;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    private static AuthConfigurationException unavailable() {
        return new AuthConfigurationException(
                "Online authentication requires WEALTHORA_SERVER_URL and a configured authentication server.");
    }
}
