package com.spendwise.auth.registration;

import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthenticationAvailability;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.GoogleOAuthStatus;
import com.spendwise.voice.SpeechApiClient;
import java.util.List;

public interface RegistrationGateway extends SpeechApiClient {

    default AuthenticationAvailability getAuthenticationAvailability() {
        return isConfigured()
                ? AuthenticationAvailability.serverUnavailable()
                : AuthenticationAvailability.serverUrlMissing();
    }

    default String getServerConnectionStatus() {
        return getAuthenticationAvailability().serverStatus();
    }

    AuthenticatedUser register(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            char[] passwordConfirmation,
            boolean termsAccepted);

    AuthenticatedUser verifyEmail(String email, String verificationCode);

    void resendVerification(String email);

    void forgotPassword(String email);

    void resetPassword(String email, String resetToken, char[] newPassword);

    void changePassword(char[] currentPassword, char[] newPassword);

    void setPassword(char[] newPassword);

    List<AccountSession> listSessions();

    void revokeSession(AccountSession session);

    void logoutAll();

    UserSession signIn(String email, char[] password);

    default GoogleOAuthStatus getGoogleOAuthStatus() {
        return new GoogleOAuthStatus(false,
                "Google Sign-In requires a configured authentication server.", "");
    }

    default UserSession continueWithGoogle() {
        throw new com.spendwise.auth.AuthConfigurationException(
                getGoogleOAuthStatus().message());
    }

    UserSession refreshSession();

    void logout();

    AuthenticatedUser getCurrentUser();

    boolean hasActiveSession();

    boolean isConfigured();
}
