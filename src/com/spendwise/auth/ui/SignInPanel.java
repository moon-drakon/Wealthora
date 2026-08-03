package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

public final class SignInPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");
    private final JCheckBox rememberMe = new JCheckBox("Remember Me");
    private final JButton signInButton;

    public SignInPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Welcome back",
                "Choose Google or use a verified NSU email and password.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);

        JButton google = primary(
                "Continue with Google", this::continueWithGoogle);
        addWide(google);
        addWide(helperLabel(
                "Requires a configured Google authentication backend. "
                        + "Wealthora never simulates a successful Google sign-in."));
        addWide(orDivider());
        addWide(sectionHeading(
                "NSU Email Access", AppBrand.NSU_EMAIL_SUBTITLE));
        addField("NSU Email", email);
        addField("Password", password);
        rememberMe.setOpaque(false);
        rememberMe.setToolTipText(
                "Session persistence will be enabled by the configured backend.");
        addWide(rememberMe);
        JButton createAccount = secondary(
                "Create Account", navigator::showSignUp);
        createAccount.setToolTipText(
                "Registration requires a configured Wealthora authentication server.");
        signInButton = primary("Sign In", this::signIn);
        addWide(buttonRow(signInButton, createAccount));
        addWide(buttonRow(secondary(
                "Forgot Password?", navigator::showForgotPassword)));
        addWide(policyLabel());
    }

    private void signIn() {
        char[] enteredPassword = password.getPassword();
        String enteredEmail = email.getText();
        signInButton.setEnabled(false);
        password.setText("");
        showStatus("Signing in securely...");
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
                    showFailure(authenticationFailure(exception));
                }
            }
        }.execute();
    }

    private void continueWithGoogle() {
        try {
            completeAuthentication(authService.continueWithGoogle());
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }

    private void completeAuthentication(UserSession session) {
        sessionManager.startSession(session);
        showSuccess("Signed in as " + session.getEmail() + ".");
        navigator.showAuthenticatedProfile(session);
    }

    private static RuntimeException authenticationFailure(
            Exception exception) {
        Throwable cause = exception
                instanceof java.util.concurrent.ExecutionException
                ? exception.getCause() : exception;
        return cause instanceof RuntimeException runtime
                ? runtime : new AuthException(
                        "Sign-in could not be completed.", cause);
    }
}
