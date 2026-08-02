package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;

public final class VerificationPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField code = textField("Verification code");

    public VerificationPanel(
            AuthService authService,
            AuthNavigator navigator) {
        super("Verify Email", "Activate your NSU password account using the "
                + "verification code delivered by the configured backend.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU Email", email);
        addField("Verification Code", code);
        addWide(buttonRow(
                primary("Verify Email", this::verify),
                secondary("Resend Code", this::resend)));
        addWide(buttonRow(secondary(
                "Back to Sign In", requiredNavigator::showSignIn)));
    }

    public void setEmail(String value) {
        email.setText(value == null ? "" : value);
    }

    private void verify() {
        try {
            AuthenticatedUser user = authService.verifyNsuEmail(
                    email.getText(), code.getText());
            showSuccess("Email verified for " + user.getEmail()
                    + ". Return to sign in.");
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }

    private void resend() {
        try {
            authService.resendVerification(email.getText());
            showSuccess("A new verification message was requested.");
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }
}
