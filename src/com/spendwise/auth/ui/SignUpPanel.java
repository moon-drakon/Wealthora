package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.StyledTextField;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JPasswordField;

public final class SignUpPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField fullName = textField("Full name");
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");
    private final JPasswordField confirmation =
            passwordField("Confirm password");

    public SignUpPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Create Account",
                "Register with an official NSU email or continue with Google.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        addWide(primary("Continue with Google", this::continueWithGoogle));
        addWide(helperLabel(
                "Google provides one unified create-or-sign-in flow."));
        addWide(orDivider());
        addWide(sectionHeading(
                "NSU Email Registration", AppBrand.NSU_EMAIL_SUBTITLE));
        addField("Full Name", fullName);
        addField("NSU Email", email);
        addField("Password", password);
        addField("Confirm Password", confirmation);
        addWide(buttonRow(
                primary("Create Account", this::createAccount),
                secondary("Back to Sign In", navigator::showSignIn)));
        addWide(helperLabel(
                "Only official @northsouth.edu addresses can be used for email registration."));
    }

    private void createAccount() {
        char[] entered = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            AuthenticatedUser account = authService.registerWithNsuEmail(
                    fullName.getText(), email.getText(), entered);
            if (account.isEmailVerified()) {
                showSuccess("Account verified. Return to sign in.");
            } else {
                navigator.showVerification(account.getEmail());
            }
        } catch (RuntimeException exception) {
            showFailure(exception);
        } finally {
            clear(entered);
            clear(repeated);
            password.setText("");
            confirmation.setText("");
        }
    }

    private void continueWithGoogle() {
        try {
            UserSession session = authService.continueWithGoogle();
            sessionManager.startSession(session);
            navigator.showAuthenticatedProfile(session);
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }
}
