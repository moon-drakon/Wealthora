package com.spendwise.auth;

import java.util.Arrays;
import java.util.Objects;

public final class BackendAuthService implements AuthService {

    private final AuthApiClient apiClient;
    private final GoogleAuthService googleAuthService;

    public BackendAuthService(AuthApiClient apiClient) {
        this(apiClient, new UnconfiguredGoogleAuthService());
    }

    public BackendAuthService(
            AuthApiClient apiClient, GoogleAuthService googleAuthService) {
        this.apiClient = Objects.requireNonNull(
                apiClient, "Authentication API client is required.");
        this.googleAuthService = Objects.requireNonNull(
                googleAuthService, "Google authentication service is required.");
    }

    @Override
    public UserSession signInWithNsuEmail(String email, char[] password) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        char[] protectedPassword = requirePassword(password).clone();
        try {
            UserSession session = requireNsuPasswordSession(
                    apiClient.signInWithNsuEmail(
                            normalizedEmail, protectedPassword));
            if (!session.getEmail().equals(normalizedEmail)) {
                throw new AuthException(
                        "Authentication response does not match the requested account.");
            }
            return session;
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    @Override
    public UserSession continueWithGoogle() {
        try (GoogleAuthorization authorization = googleAuthService.authorize()) {
            char[] code = authorization.copyAuthorizationCode();
            try {
                return requireGoogleSession(apiClient.continueWithGoogle(
                        code, authorization.getRedirectUri()));
            } finally {
                Arrays.fill(code, '\0');
            }
        }
    }

    @Override
    public AuthenticatedUser registerWithNsuEmail(
            String fullName, String email, char[] password) {
        String name = required(fullName, "Full name");
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        char[] protectedPassword = requirePassword(password).clone();
        try {
            AuthenticatedUser user = requireLocalUser(
                    apiClient.registerWithNsuEmail(
                            name, normalizedEmail, protectedPassword));
            if (!user.getEmail().equals(normalizedEmail)) {
                throw new AuthException(
                        "Registration response does not match the requested account.");
            }
            return user;
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    @Override
    public AuthenticatedUser verifyNsuEmail(
            String email, String verificationCode) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        AuthenticatedUser user = requireLocalUser(apiClient.verifyNsuEmail(
                normalizedEmail,
                required(verificationCode, "Verification code")));
        if (!user.getEmail().equals(normalizedEmail)
                || !user.isEmailVerified()
                || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AuthException(
                    "The backend did not confirm an active verified NSU account.");
        }
        return user;
    }

    @Override
    public void resendVerification(String email) {
        apiClient.resendVerification(
                NsuEmailPolicy.requireInstitutionalEmail(email));
    }

    @Override
    public void forgotPassword(String email) {
        apiClient.forgotPassword(
                NsuEmailPolicy.requireInstitutionalEmail(email));
    }

    @Override
    public void resetPassword(
            String email, String resetToken, char[] newPassword) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        String token = required(resetToken, "Reset token");
        char[] protectedPassword = requirePassword(newPassword).clone();
        try {
            apiClient.resetPassword(
                    normalizedEmail, token, protectedPassword);
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    @Override
    public UserSession refreshSession() {
        return requireValidSession(apiClient.refreshSession());
    }

    @Override
    public void logout() {
        apiClient.logout();
    }

    @Override
    public AuthenticatedUser getCurrentUser() {
        return requireSupportedUser(apiClient.getCurrentUser());
    }

    private static UserSession requireNsuPasswordSession(UserSession session) {
        UserSession required = requireValidSession(session);
        NsuEmailPolicy.requireInstitutionalEmail(required.getEmail());
        if (required.getProvider() == AuthProvider.GOOGLE) {
            throw new AuthException(
                    "Google-only accounts cannot use password sign-in.");
        }
        return required;
    }

    private static UserSession requireGoogleSession(UserSession session) {
        UserSession required = requireValidSession(session);
        if (required.getProvider() == AuthProvider.LOCAL
                || required.getGoogleSubjectId().isBlank()) {
            throw new AuthException(
                    "The backend did not return a verified Google identity.");
        }
        return required;
    }

    private static UserSession requireValidSession(UserSession session) {
        UserSession required = Objects.requireNonNull(
                session, "Authentication API returned no session.");
        requireSupportedUser(required.getUser());
        return required;
    }

    private static AuthenticatedUser requireLocalUser(AuthenticatedUser user) {
        AuthenticatedUser required = requireSupportedUser(user);
        NsuEmailPolicy.requireInstitutionalEmail(required.getEmail());
        if (required.getPrimaryAuthProvider() == AuthProvider.GOOGLE) {
            throw new AuthException(
                    "Password accounts must use the local authentication provider.");
        }
        return required;
    }

    private static AuthenticatedUser requireSupportedUser(
            AuthenticatedUser user) {
        return Objects.requireNonNull(
                user, "Authentication API returned no user.");
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
