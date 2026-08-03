package com.spendwise.auth.registration;

import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.UserSession;

public interface RegistrationGateway {

    AuthenticatedUser register(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            char[] passwordConfirmation,
            boolean termsAccepted);

    AuthenticatedUser verifyEmail(String email, String verificationCode);

    void resendVerification(String email);

    UserSession signIn(String email, char[] password);

    UserSession refreshSession();

    void logout();

    AuthenticatedUser getCurrentUser();

    boolean hasActiveSession();

    boolean isConfigured();
}
