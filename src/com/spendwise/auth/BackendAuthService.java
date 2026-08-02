package com.spendwise.auth;

import java.util.Arrays;
import java.util.Objects;

public final class BackendAuthService implements AuthService {

    private final AuthApiClient apiClient;

    public BackendAuthService(AuthApiClient apiClient) {
        this.apiClient = Objects.requireNonNull(
                apiClient, "Authentication API client is required.");
    }

    @Override
    public UserSession signIn(String email, char[] password) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        char[] protectedPassword = requirePassword(password).clone();
        try {
            return requireVerified(apiClient.signIn(
                    normalizedEmail, protectedPassword));
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    @Override
    public UserSession continueWithGoogle() {
        return requireVerified(apiClient.signInWithGoogle());
    }

    @Override
    public UserSession createAccount(
            String displayName, String email, char[] password) {
        if (displayName == null || displayName.isBlank()) {
            throw new AuthException("Display name is required.");
        }
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        char[] protectedPassword = requirePassword(password).clone();
        try {
            return Objects.requireNonNull(apiClient.createAccount(
                    displayName.strip(), normalizedEmail, protectedPassword),
                    "Authentication API returned no account.");
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    @Override
    public UserSession verifyEmail(String email, String verificationCode) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        String code = required(verificationCode, "Verification code");
        return requireVerified(apiClient.verifyEmail(normalizedEmail, code));
    }

    @Override
    public void requestPasswordReset(String email) {
        apiClient.requestPasswordReset(
                NsuEmailPolicy.requireInstitutionalEmail(email));
    }

    @Override
    public void resetPassword(String resetToken, char[] newPassword) {
        String token = required(resetToken, "Reset token");
        char[] protectedPassword = requirePassword(newPassword).clone();
        try {
            apiClient.resetPassword(token, protectedPassword);
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    private static UserSession requireVerified(UserSession session) {
        UserSession required = Objects.requireNonNull(
                session, "Authentication API returned no session.");
        NsuEmailPolicy.requireInstitutionalEmail(required.getEmail());
        if (!required.isVerified()) {
            throw new AuthException(
                    "Verify your @northsouth.edu email before signing in.");
        }
        return required;
    }

    private static char[] requirePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new AuthException(
                    "Password must contain at least 8 characters.");
        }
        return password;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuthException(fieldName + " is required.");
        }
        return value.strip();
    }
}
