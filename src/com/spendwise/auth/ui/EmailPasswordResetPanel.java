package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.otp.EmailOtpAccountService;
import com.spendwise.auth.otp.EmailOtpChallenge;
import com.spendwise.ui.component.StyledTextField;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

/** Email-OTP password reset that changes only the local password hash. */
public final class EmailPasswordResetPanel extends AuthFormPanel {

    private final EmailOtpAccountService otpAccounts;
    private final AuthNavigator navigator;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField code = textField("Six-digit code");
    private final JPasswordField password = passwordField("New password");
    private final JPasswordField confirmation =
            passwordField("Confirm new password");
    private final JButton sendButton;
    private final JButton resendButton;
    private final JButton resetButton;
    private final JButton returnButton;
    private EmailOtpChallenge challenge;
    private boolean completed;

    public EmailPasswordResetPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Reset with Email OTP",
                "Verify the local account email, then update only its local BCrypt password hash.");
        otpAccounts = authService instanceof EmailOtpAccountService otp
                ? otp : null;
        this.navigator = Objects.requireNonNull(navigator);
        addField("NSU Email", email);
        sendButton = primary("Send Reset Code", this::send);
        resendButton = secondary("Resend Code", this::resend);
        addWide(buttonRow(sendButton, resendButton));
        addField("Verification Code", code);
        addField("New Password", password);
        addField("Confirm New Password", confirmation);
        addWide(helperLabel(
                "Use 6-128 characters with an English letter and number. The new password is never sent to the relay."));
        resetButton = primary("Verify Code and Reset Password", this::reset);
        returnButton = secondary("Other Recovery Options", this::leave);
        addWide(buttonRow(resetButton, returnButton));
        setChallengeControls(false);
    }

    private void send() {
        if (otpAccounts == null) {
            showFailure(new AuthException(
                    "Email OTP is not configured. Use Offline Recovery."));
            return;
        }
        String enteredEmail = email.getText();
        setWorking(true, "Requesting a password-reset code...");
        new SwingWorker<EmailOtpChallenge, Void>() {
            @Override
            protected EmailOtpChallenge doInBackground() {
                return otpAccounts.beginPasswordReset(enteredEmail);
            }

            @Override
            protected void done() {
                setWorking(false, " ");
                try {
                    challenge = get();
                    completed = false;
                    returnButton.setText("Other Recovery Options");
                    email.setText(challenge.email());
                    email.setEditable(false);
                    setChallengeControls(true);
                    showSuccess("If the local account is eligible, a reset code was requested.");
                } catch (Exception exception) {
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }

    private void resend() {
        EmailOtpChallenge active = requiredChallenge();
        if (Instant.now().isBefore(active.resendAvailableAt())) {
            showFailure(new AuthException(
                    "Wait at least sixty seconds before requesting another code."));
            return;
        }
        setWorking(true, "Requesting a replacement reset code...");
        new SwingWorker<EmailOtpChallenge, Void>() {
            @Override
            protected EmailOtpChallenge doInBackground() {
                return otpAccounts.resendPasswordReset(
                        active.challengeIdentifier());
            }

            @Override
            protected void done() {
                setWorking(false, " ");
                try {
                    challenge = get();
                    code.setText("");
                    showSuccess("A replacement code was requested. The previous code is no longer valid.");
                } catch (Exception exception) {
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }

    private void reset() {
        EmailOtpChallenge active = requiredChallenge();
        char[] nextPassword = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!Arrays.equals(nextPassword, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            String enteredCode = code.getText();
            password.setText("");
            confirmation.setText("");
            setWorking(true, "Verifying the code and updating the local password...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        otpAccounts.completePasswordReset(
                                active.challengeIdentifier(), enteredCode,
                                nextPassword, repeated);
                        return null;
                    } finally {
                        clear(nextPassword);
                        clear(repeated);
                    }
                }

                @Override
                protected void done() {
                    try {
                        get();
                        challenge = null;
                        completed = true;
                        code.setText("");
                        setChallengeControls(false);
                        sendButton.setEnabled(false);
                        returnButton.setText("Back to Sign In");
                        showSuccess("Password reset completed. Return to Sign In.");
                    } catch (Exception exception) {
                        setWorking(false, " ");
                        showFailure(workerFailure(exception));
                    }
                }
            }.execute();
        } catch (RuntimeException exception) {
            clear(nextPassword);
            clear(repeated);
            showFailure(exception);
        }
    }

    private void leave() {
        if (challenge != null && otpAccounts != null) {
            otpAccounts.cancelPasswordReset(challenge.challengeIdentifier());
        }
        challenge = null;
        code.setText("");
        password.setText("");
        confirmation.setText("");
        email.setEditable(true);
        setChallengeControls(false);
        if (completed) {
            completed = false;
            returnButton.setText("Other Recovery Options");
            navigator.showSignIn();
        } else {
            navigator.showForgotPassword();
        }
    }

    private EmailOtpChallenge requiredChallenge() {
        if (otpAccounts == null || challenge == null) {
            throw new AuthException("Request a password-reset code first.");
        }
        return challenge;
    }

    private void setWorking(boolean working, String message) {
        sendButton.setEnabled(!working && challenge == null);
        resendButton.setEnabled(!working && challenge != null);
        resetButton.setEnabled(!working && challenge != null);
        showStatus(message);
    }

    private void setChallengeControls(boolean enabled) {
        code.setEnabled(enabled);
        password.setEnabled(enabled);
        confirmation.setEnabled(enabled);
        resendButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        sendButton.setEnabled(!enabled);
    }
}
