package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;

public final class ForgotPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");

    public ForgotPasswordPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Forgot password", "Request a password-reset message from "
                + "the configured authentication backend.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU email", email);
        addWide(buttonRow(
                primary("Request reset", this::requestReset),
                secondary("Enter reset token",
                        requiredNavigator::showResetPassword)));
        addWide(buttonRow(secondary(
                "Back to sign in", requiredNavigator::showSignIn)));
    }

    private void requestReset() {
        try {
            authService.requestPasswordReset(email.getText());
            showSuccess("Password-reset instructions requested.");
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }
}
