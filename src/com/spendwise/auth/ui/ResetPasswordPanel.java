package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.ui.component.StyledTextField;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

public final class ResetPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField token = textField("Reset token");
    private final JPasswordField password = passwordField("New password");
    private final JPasswordField confirmation =
            passwordField("Confirm new password");
    private final JButton resetButton;

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
        resetButton = primary("Reset Password", this::resetPassword);
        addWide(buttonRow(
                resetButton,
                secondary("Back to Sign In", requiredNavigator::showSignIn)));
    }

    private void resetPassword() {
        char[] entered = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            String enteredEmail = email.getText();
            String enteredToken = token.getText();
            resetButton.setEnabled(false);
            password.setText("");
            confirmation.setText("");
            showStatus("Resetting password securely...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        authService.resetPassword(
                                enteredEmail, enteredToken, entered);
                        return null;
                    } finally {
                        clear(entered);
                    }
                }

                @Override
                protected void done() {
                    resetButton.setEnabled(true);
                    try {
                        get();
                        token.setText("");
                        showSuccess(
                                "Password reset completed. Return to sign in.");
                    } catch (Exception exception) {
                        showFailure(workerFailure(exception));
                    }
                }
            }.execute();
        } catch (RuntimeException exception) {
            showFailure(exception);
            clear(entered);
        }
        clear(repeated);
    }
}
