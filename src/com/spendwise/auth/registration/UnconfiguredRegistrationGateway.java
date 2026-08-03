package com.spendwise.auth.registration;

import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthenticatedUser;

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
    public boolean isConfigured() {
        return false;
    }

    private static AuthConfigurationException unavailable() {
        return new AuthConfigurationException(
                "Account registration requires WEALTHORA_SERVER_URL and a configured authentication server.");
    }
}
