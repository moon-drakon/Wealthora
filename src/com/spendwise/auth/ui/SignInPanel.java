package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.LocalAccountService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

public final class SignInPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");
    private final JButton signInButton;

    public SignInPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Welcome back", "Sign in to your portable local Wealthora workspace.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        addWide(sectionHeading("Local Sign In",
                "Finance records stay inside this project's data folder."));
        addField("NSU Email", email);
        addField("Password", password);
        signInButton = primary("Sign In", this::signIn);
        if (authService instanceof LocalAccountService) {
            addWide(buttonRow(signInButton,
                    secondary("Create Account", navigator::showSignUp),
                    secondary("Forgot Password?",
                            navigator::showForgotPassword)));
        } else {
            addWide(buttonRow(signInButton));
        }
        addWide(policyLabel());
    }

    private void signIn() {
        char[] enteredPassword = password.getPassword();
        String enteredEmail = email.getText();
        signInButton.setEnabled(false);
        password.setText("");
        showStatus("Signing in...");
        new SwingWorker<UserSession, Void>() {
            @Override
            protected UserSession doInBackground() {
                try {
                    return authService.signInWithNsuEmail(
                            enteredEmail, enteredPassword);
                } finally {
                    clear(enteredPassword);
                }
            }

            @Override
            protected void done() {
                signInButton.setEnabled(true);
                try {
                    completeAuthentication(get());
                } catch (Exception exception) {
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }

    private void completeAuthentication(UserSession session) {
        sessionManager.startSession(session);
        showSuccess("Signed in as " + session.getEmail() + ".");
        navigator.showAuthenticatedProfile(session);
    }
}
