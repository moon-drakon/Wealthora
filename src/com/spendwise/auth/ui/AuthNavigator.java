package com.spendwise.auth.ui;

public interface AuthNavigator {

    void showSignIn();

    void showSignUp();

    void showVerification(String email);

    void showForgotPassword();

    void showResetPassword();
}
