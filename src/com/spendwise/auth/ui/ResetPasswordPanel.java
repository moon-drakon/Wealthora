package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.ui.component.StyledTextField;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JPasswordField;

public final class ResetPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField token = textField("Reset token");
    private final JPasswordField password = passwordField("New password");
    private final JPasswordField confirmation =
            passwordField("Confirm new password");

    public ResetPasswordPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Reset password", "Set a new password using the reset token "
                + "issued by the configured backend.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addField("Reset token", token);
        addField("New password", password);
        addField("Confirm new password", confirmation);
        addWide(buttonRow(
                primary("Reset password", this::resetPassword),
                secondary("Back to sign in", requiredNavigator::showSignIn)));
    }

    private void resetPassword() {
        char[] entered = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            authService.resetPassword(token.getText(), entered);
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
