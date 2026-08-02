package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;

public final class ForgotPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");

    public ForgotPasswordPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Forgot Password", "Request a password-reset message for a "
                + "verified NSU password account.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU Email", email);
        addWide(buttonRow(
                primary("Request Reset", this::requestReset),
                secondary("Enter Reset Token",
                        requiredNavigator::showResetPassword)));
        addWide(buttonRow(secondary(
                "Back to Sign In", requiredNavigator::showSignIn)));
    }

    private void requestReset() {
        try {
            authService.forgotPassword(email.getText());
            showSuccess("Password-reset instructions requested.");
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }
}
