package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.ui.component.StyledTextField;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JPasswordField;

public final class ResetPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField token = textField("Reset token");
    private final JPasswordField password = passwordField("New password");
    private final JPasswordField confirmation =
            passwordField("Confirm new password");

    public ResetPasswordPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Reset Password", "Set a new password for a verified NSU "
                + "account using the backend-issued reset token.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU Email", email);
        addField("Reset Token", token);
        addField("New Password", password);
        addField("Confirm New Password", confirmation);
        addWide(buttonRow(
                primary("Reset Password", this::resetPassword),
                secondary("Back to Sign In", requiredNavigator::showSignIn)));
    }

    private void resetPassword() {
        char[] entered = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            authService.resetPassword(
                    email.getText(), token.getText(), entered);
            showSuccess("Password reset completed. Return to sign in.");
        } catch (RuntimeException exception) {
            showFailure(exception);
        } finally {
            clear(entered);
            clear(repeated);
            password.setText("");
            confirmation.setText("");
        }
    }
}
