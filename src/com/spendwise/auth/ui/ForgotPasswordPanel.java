package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.SwingWorker;

public final class ForgotPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");
    private final JButton requestButton;

    public ForgotPasswordPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Forgot Password", "Request a password-reset message for a "
                + "verified NSU password account.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU Email", email);
        requestButton = primary("Request Reset", this::requestReset);
        addWide(buttonRow(
                requestButton,
                secondary("Enter Reset Token",
                        requiredNavigator::showResetPassword)));
        addWide(buttonRow(secondary(
                "Back to Sign In", requiredNavigator::showSignIn)));
    }

    private void requestReset() {
        String enteredEmail = email.getText();
        requestButton.setEnabled(false);
        showStatus("Requesting password-reset instructions...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                authService.forgotPassword(enteredEmail);
                return null;
            }

            @Override
            protected void done() {
                requestButton.setEnabled(true);
                try {
                    get();
                    showSuccess("If the account is eligible, reset instructions were sent.");
                } catch (Exception exception) {
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }
}
