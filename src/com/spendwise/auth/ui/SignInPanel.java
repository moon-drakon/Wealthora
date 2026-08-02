package com.spendwise.auth.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPasswordField;

public final class SignInPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");

    public SignInPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Sign in", "Access your " + AppBrand.APP_NAME + " profile when a real "
                + "authentication backend is configured.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);

        addWide(policyLabel());
        JButton google = secondary(
                "Continue with Google", this::continueWithGoogle);
        addWide(google);
        addField("NSU email", email);
        addField("Password", password);
        addWide(buttonRow(
                primary("Sign in", this::signIn),
                secondary("Forgot password", requiredNavigator::showForgotPassword)));
        addWide(buttonRow(secondary(
                "Create account", requiredNavigator::showSignUp)));
    }

    private void signIn() {
        char[] enteredPassword = password.getPassword();
        try {
            UserSession session = authService.signIn(
                    email.getText(), enteredPassword);
            sessionManager.startSession(session);
            showSuccess("Signed in as " + session.getEmail() + ".");
        } catch (RuntimeException exception) {
            showFailure(exception);
        } finally {
            clear(enteredPassword);
            password.setText("");
        }
    }

    private void continueWithGoogle() {
        try {
            UserSession session = authService.continueWithGoogle();
            sessionManager.startSession(session);
            showSuccess("Signed in as " + session.getEmail() + ".");
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }
}
