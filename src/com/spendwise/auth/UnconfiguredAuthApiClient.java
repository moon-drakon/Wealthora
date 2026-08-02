package com.spendwise.auth;

public final class UnconfiguredAuthApiClient implements AuthApiClient {

    @Override
    public UserSession signIn(String email, char[] password) {
        throw new AuthConfigurationException();
    }

    @Override
    public UserSession signInWithGoogle() {
        throw new AuthConfigurationException();
    }

    @Override
    public UserSession createAccount(
            String displayName, String email, char[] password) {
        throw new AuthConfigurationException();
    }

    @Override
    public UserSession verifyEmail(String email, String verificationCode) {
        throw new AuthConfigurationException();
    }

    @Override
    public void requestPasswordReset(String email) {
        throw new AuthConfigurationException();
    }

    @Override
    public void resetPassword(String resetToken, char[] newPassword) {
        throw new AuthConfigurationException();
    }
}
