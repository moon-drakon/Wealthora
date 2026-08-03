package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticationAvailability;
import com.spendwise.auth.FinanceMode;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

public final class SignInPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");
    private final JCheckBox rememberMe = new JCheckBox("Remember Me");
    private final JButton signInButton;
    private final JButton localSignInButton;
    private final JButton googleButton;
    private final JLabel serverStatus = new JLabel("Server: checking...");
    private final JLabel emailStatus = new JLabel("Email: checking...");
    private final JLabel googleStatus = new JLabel("Google: checking...");

    public SignInPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Welcome back",
                "Choose Google or use a verified NSU email and password.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);

        googleButton = primary(
                "Continue with Google", this::continueWithGoogle);
        addWide(googleButton);
        addWide(helperLabel(
                "Requires a configured Google authentication backend. "
                        + "Wealthora never simulates a successful Google sign-in."));
        addWide(orDivider());
        addWide(sectionHeading(
                "NSU Email Access", AppBrand.NSU_EMAIL_SUBTITLE));
        addWide(serverStatus);
        addWide(emailStatus);
        addWide(googleStatus);
        addField("NSU Email", email);
        addField("Password", password);
        addWide(helperLabel(
                "Choose CLOUD for server data or LOCAL for data stored only "
                        + "on this device."));
        rememberMe.setOpaque(false);
        rememberMe.setToolTipText(
                "Session persistence will be enabled by the configured backend.");
        addWide(rememberMe);
        JButton createAccount = secondary(
                "Create Account", navigator::showSignUp);
        createAccount.setToolTipText(
                "Registration requires a configured Wealthora authentication server.");
        signInButton = primary(
                "Sign In to CLOUD", () -> signIn(FinanceMode.CLOUD));
        localSignInButton = secondary(
                "Sign In to LOCAL", () -> signIn(FinanceMode.LOCAL));
        addWide(buttonRow(signInButton, createAccount));
        addWide(buttonRow(localSignInButton));
        addWide(buttonRow(secondary(
                "Forgot Password?", navigator::showForgotPassword)));
        addWide(policyLabel());
        refreshServerStatus();
    }

    private void refreshServerStatus() {
        new SwingWorker<AuthenticationAvailability, Void>() {
            @Override
            protected AuthenticationAvailability doInBackground() {
                return authService.getAuthenticationAvailability();
            }

            @Override
            protected void done() {
                try {
                    AuthenticationAvailability availability = get();
                    serverStatus.setText(
                            "Server: " + availability.serverStatus());
                    emailStatus.setText(
                            "Email: " + availability.emailStatus());
                    googleStatus.setText(
                            "Google: " + availability.googleStatus());
                } catch (Exception exception) {
                    serverStatus.setText("Server: Server unavailable");
                    emailStatus.setText("Email: Email provider unavailable");
                    googleStatus.setText("Google: Google OAuth unavailable");
                }
            }
        }.execute();
    }

    private void signIn(FinanceMode destination) {
        char[] enteredPassword = password.getPassword();
        String enteredEmail = email.getText();
        signInButton.setEnabled(false);
        localSignInButton.setEnabled(false);
        password.setText("");
        showStatus("Signing in to " + destination + " securely...");
        new SwingWorker<UserSession, Void>() {
            @Override
            protected UserSession doInBackground() {
                try {
                    return authService.signInWithNsuEmail(
                            enteredEmail, enteredPassword,
                            destination);
                } finally {
                    clear(enteredPassword);
                }
            }

            @Override
            protected void done() {
                signInButton.setEnabled(true);
                localSignInButton.setEnabled(true);
                try {
                    completeAuthentication(get());
                } catch (Exception exception) {
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }

    private void continueWithGoogle() {
        googleButton.setEnabled(false);
        showStatus("Opening secure Google Sign-In in your browser...");
        new SwingWorker<UserSession, Void>() {
            @Override
            protected UserSession doInBackground() {
                return authService.continueWithGoogle();
            }

            @Override
            protected void done() {
                googleButton.setEnabled(true);
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
