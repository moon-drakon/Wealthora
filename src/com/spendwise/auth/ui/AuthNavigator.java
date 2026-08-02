package com.spendwise.auth.ui;

import com.spendwise.auth.UserSession;

public interface AuthNavigator {

    void showSignIn();

    void showSignUp();

    void showVerification(String email);

    void showForgotPassword();

    void showResetPassword();

    void showAuthenticatedProfile(UserSession session);
}
