package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;

public final class VerificationPanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField code = textField("Verification code");

    public VerificationPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Verify email", "Enter the verification code delivered by "
                + "the configured authentication backend.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU email", email);
        addField("Verification code", code);
        addWide(buttonRow(
                primary("Verify email", this::verify),
                secondary("Back to sign in", requiredNavigator::showSignIn)));
    }

    public void setEmail(String value) {
        email.setText(value == null ? "" : value);
    }

    private void verify() {
        try {
            UserSession session = authService.verifyEmail(
                    email.getText(), code.getText());
            sessionManager.startSession(session);
            showSuccess("Email verified for " + session.getEmail() + ".");
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }
}
