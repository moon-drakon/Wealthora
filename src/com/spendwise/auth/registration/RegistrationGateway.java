package com.spendwise.auth.registration;

import com.spendwise.auth.AuthenticatedUser;

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

    boolean isConfigured();
}
