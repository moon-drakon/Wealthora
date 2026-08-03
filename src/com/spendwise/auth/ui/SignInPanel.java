package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;

public final class SignInPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");
    private final JCheckBox rememberMe = new JCheckBox("Remember Me");

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
        addWide(buttonRow(primary("Sign In", this::signIn), createAccount));
        addWide(buttonRow(secondary(
                "Forgot Password?", navigator::showForgotPassword)));
        addWide(policyLabel());
    }

    private void signIn() {
        char[] enteredPassword = password.getPassword();
        try {
            UserSession session = authService.signInWithNsuEmail(
                    email.getText(), enteredPassword);
            completeAuthentication(session);
        } catch (RuntimeException exception) {
            showFailure(exception);
        } finally {
            clear(enteredPassword);
            password.setText("");
        }
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
}
