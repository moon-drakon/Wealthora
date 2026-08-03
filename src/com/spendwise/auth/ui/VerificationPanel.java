package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.AccountStatus;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.SwingWorker;

public final class VerificationPanel extends AuthFormPanel {

    private final AuthService authService;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField code = textField("Verification code");
    private final JButton verifyButton;
    private final JButton resendButton;

    public VerificationPanel(
            AuthService authService,
            AuthNavigator navigator) {
        super("Verify Email", "Activate your NSU password account using the "
                + "six-digit verification code delivered by the configured backend.");
        this.authService = Objects.requireNonNull(authService);
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU Email", email);
        addField("Verification Code", code);
        verifyButton = primary("Verify Email", this::verify);
        resendButton = secondary("Resend Code", this::resend);
        addWide(buttonRow(verifyButton, resendButton));
        addWide(buttonRow(secondary(
                "Back to Sign In", requiredNavigator::showSignIn)));
    }

    public void setEmail(String value) {
        email.setText(value == null ? "" : value);
    }

    private void verify() {
        String enteredEmail = email.getText();
        String enteredCode = code.getText();
        setWorking(true, "Verifying the one-time code...");
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override protected AuthenticatedUser doInBackground() {
                return authService.verifyNsuEmail(enteredEmail, enteredCode);
            }
            @Override protected void done() {
                setWorking(false, " ");
                try {
                    AuthenticatedUser user = get();
                    code.setText("");
                    if (user.getAccountStatus()
                            == AccountStatus.PENDING_APPROVAL) {
                        showSuccess("Email verified. Your account is awaiting administrator approval.");
                    } else {
                        showSuccess("Email verified for " + user.getEmail()
                                + ". Return to sign in.");
                    }
                } catch (Exception exception) {
                    showFailure(authenticationFailure(exception));
                }
            }
        }.execute();
    }

    private void resend() {
        String enteredEmail = email.getText();
        setWorking(true, "Requesting a new verification message...");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                authService.resendVerification(enteredEmail);
                return null;
            }
            @Override protected void done() {
                setWorking(false, " ");
                try {
                    get();
                    showSuccess("A new verification message was requested.");
                } catch (Exception exception) {
                    showFailure(authenticationFailure(exception));
                }
            }
        }.execute();
    }

    private void setWorking(boolean working, String message) {
        verifyButton.setEnabled(!working);
        resendButton.setEnabled(!working);
        showStatus(message);
    }

    private static RuntimeException authenticationFailure(Exception exception) {
        Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                ? exception.getCause() : exception;
        return cause instanceof RuntimeException runtime
                ? runtime : new com.spendwise.auth.AuthException(
                        "Email verification could not be completed.", cause);
    }
}
