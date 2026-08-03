package com.spendwise.auth.registration;

import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.UserSession;
import com.spendwise.voice.SpeechApiClient;
import java.util.List;

public interface RegistrationGateway extends SpeechApiClient {

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

    UserSession refreshSession();

    void logout();

    AuthenticatedUser getCurrentUser();

    boolean hasActiveSession();

    boolean isConfigured();
}
