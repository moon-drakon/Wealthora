package com.spendwise.auth.registration;

import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.UserSession;

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
