package com.spendwise.auth.ui;

import com.spendwise.auth.UserSession;
import com.spendwise.auth.otp.EmailOtpChallenge;

public interface AuthNavigator {

    void showOwnerSetup();

    void showSignIn();

    void showSignUp();

    void showVerification(String email);

    default void showRegistrationVerification(EmailOtpChallenge challenge) {
        showVerification(challenge.email());
    }

    void showForgotPassword();

    default void showEmailPasswordReset() {
        showForgotPassword();
    }

    default void showOfflineRecovery() {
        showForgotPassword();
    }

    void showAuthenticatedProfile(UserSession session);
}
