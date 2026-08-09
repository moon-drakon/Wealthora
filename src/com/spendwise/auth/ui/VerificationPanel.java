package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.otp.EmailOtpAccountService;
import com.spendwise.auth.otp.EmailOtpChallenge;
import com.spendwise.ui.component.StyledTextField;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.SwingWorker;

/** Registration verification without storing an account before OTP success. */
public final class VerificationPanel extends AuthFormPanel {

    private final EmailOtpAccountService otpAccounts;
    private final AuthNavigator navigator;
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField code = textField("Six-digit code");
    private final JButton verifyButton;
    private final JButton resendButton;
    private final JButton returnButton;
    private EmailOtpChallenge challenge;

    public VerificationPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Verify Registration",
                "Enter the six-digit code sent for this local account registration.");
        this.otpAccounts = authService instanceof EmailOtpAccountService otp
                ? otp : null;
        this.navigator = Objects.requireNonNull(navigator);
        email.setEditable(false);
        email.setFocusable(false);
        addField("NSU Email", email);
        addField("Verification Code", code);
        verifyButton = primary("Verify and Create Account", this::verify);
        resendButton = secondary("Resend Code", this::resend);
        returnButton = secondary("Cancel Registration", this::cancel);
        addWide(buttonRow(verifyButton, resendButton));
        addWide(buttonRow(returnButton));
        addWide(helperLabel(
                "The code expires within ten minutes. Five incorrect attempts invalidate it."));
        setControls(false);
    }

    public void setChallenge(EmailOtpChallenge value) {
        challenge = Objects.requireNonNull(value);
        email.setText(value.email());
        code.setText("");
        returnButton.setText("Cancel Registration");
        setControls(true);
        showSuccess("Verification code requested. " + resendMessage(value));
    }

    /** Compatibility helper for previews that have no live challenge. */
    public void setEmail(String value) {
        email.setText(value == null ? "" : value);
    }

    private void verify() {
        EmailOtpChallenge active = requiredChallenge();
        String enteredCode = code.getText();
        setWorking(true, "Verifying the code and creating the local account...");
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override
            protected AuthenticatedUser doInBackground() {
                return otpAccounts.verifyRegistration(
                        active.challengeIdentifier(), enteredCode);
            }

            @Override
            protected void done() {
                try {
                    AuthenticatedUser account = get();
                    challenge = null;
                    code.setText("");
                    verifyButton.setEnabled(false);
                    resendButton.setEnabled(false);
                    returnButton.setText("Back to Sign In");
                    showSuccess("Account created for " + account.getEmail()
                            + ". Return to Sign In.");
                } catch (Exception exception) {
                    setWorking(false, " ");
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }

    private void resend() {
        EmailOtpChallenge active = requiredChallenge();
        if (Instant.now().isBefore(active.resendAvailableAt())) {
            showFailure(new AuthException(resendMessage(active)));
            return;
        }
        setWorking(true, "Requesting a replacement verification code...");
        new SwingWorker<EmailOtpChallenge, Void>() {
            @Override
            protected EmailOtpChallenge doInBackground() {
                return otpAccounts.resendRegistration(
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

    private void cancel() {
        if (challenge != null && otpAccounts != null) {
            otpAccounts.cancelRegistration(challenge.challengeIdentifier());
        }
        challenge = null;
        code.setText("");
        setControls(false);
        navigator.showSignIn();
    }

    private EmailOtpChallenge requiredChallenge() {
        if (otpAccounts == null || challenge == null) {
            throw new AuthException(
                    "No registration verification is active.");
        }
        return challenge;
    }

    private void setWorking(boolean working, String message) {
        verifyButton.setEnabled(!working && challenge != null);
        resendButton.setEnabled(!working && challenge != null);
        showStatus(message);
    }

    private void setControls(boolean enabled) {
        code.setEnabled(enabled);
        verifyButton.setEnabled(enabled);
        resendButton.setEnabled(enabled);
    }

    private static String resendMessage(EmailOtpChallenge value) {
        long seconds = Math.max(0, Duration.between(
                Instant.now(), value.resendAvailableAt()).toSeconds());
        return seconds == 0 ? "You may request another code."
                : "Another code can be requested in about " + seconds
                        + " seconds.";
    }
}
