package com.spendwise.auth;

public final class UnconfiguredAuthApiClient implements AuthApiClient {

    @Override
    public UserSession signInWithNsuEmail(String email, char[] password) {
        throw new AuthConfigurationException();
    }

    @Override
    public UserSession continueWithGoogle(
            char[] authorizationCode, String redirectUri) {
        throw new AuthConfigurationException();
    }

    @Override
    public AuthenticatedUser registerWithNsuEmail(
            String fullName, String email, char[] password) {
        throw new AuthConfigurationException();
    }

    @Override
    public AuthenticatedUser verifyNsuEmail(
            String email, String verificationCode) {
        throw new AuthConfigurationException();
    }

    @Override
    public void resendVerification(String email) {
        throw new AuthConfigurationException();
    }

    @Override
    public void forgotPassword(String email) {
        throw new AuthConfigurationException();
    }

    @Override
    public void resetPassword(
            String email, String resetToken, char[] newPassword) {
        throw new AuthConfigurationException();
    }

    @Override
    public UserSession refreshSession() {
        throw new AuthConfigurationException();
    }

    @Override
    public void logout() {
        throw new AuthConfigurationException();
    }

    @Override
    public AuthenticatedUser getCurrentUser() {
        throw new AuthConfigurationException();
    }
}
