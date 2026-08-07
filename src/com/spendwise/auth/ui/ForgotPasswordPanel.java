package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.LocalAccountService;
import com.spendwise.auth.PasswordRecoveryChallenge;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

public final class ForgotPasswordPanel extends AuthFormPanel {

    private final AuthService authService;
    private final LocalAccountService localAccountService;
    private final StyledTextField email = textField("NSU email");
    private final JLabel question = new JLabel("Find your account first.");
    private final JLabel hint = new JLabel(" ");
    private final JPasswordField recoveryAnswer =
            passwordField("Recovery answer");
    private final JPasswordField password = passwordField("New password");
    private final JPasswordField confirmation =
            passwordField("Confirm new password");
    private final JButton requestButton;
    private final JButton resetButton;

    public ForgotPasswordPanel(
            AuthService authService, AuthNavigator navigator) {
        super("Forgot Password",
                usesLocalRecovery(authService)
                        ? "Recover a local account with its protected answer, or ask an administrator for help."
                        : "Request a password-reset message for a verified NSU password account.");
        this.authService = Objects.requireNonNull(authService);
        this.localAccountService = usesLocalRecovery(authService)
                && authService instanceof LocalAccountService local
                ? local : null;
        AuthNavigator requiredNavigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("NSU Email", email);
        requestButton = primary(localAccountService == null
                ? "Request Reset" : "Find Account", this::requestReset);
        if (localAccountService == null) {
            resetButton = null;
            addWide(buttonRow(
                    requestButton,
                    secondary("Enter Reset Token",
                            requiredNavigator::showResetPassword)));
        } else {
            addWide(requestButton);
            question.setFont(AppFonts.button());
            AppTheme.mark(question, AppTheme.PRIMARY_TEXT_ROLE);
            hint.setFont(AppFonts.caption());
            AppTheme.mark(hint, AppTheme.SECONDARY_TEXT_ROLE);
            addWide(question);
            addWide(hint);
            addField("Recovery Answer", recoveryAnswer);
            addField("New Password", password);
            addField("Confirm New Password", confirmation);
            recoveryAnswer.setEnabled(false);
            password.setEnabled(false);
            confirmation.setEnabled(false);
            resetButton = primary("Reset Password", this::resetLocally);
            resetButton.setEnabled(false);
            addWide(buttonRow(resetButton));
            addWide(helperLabel(
                    "After five incorrect attempts, recovery is temporarily locked for 15 minutes. The OWNER or an administrator can reset another user's password from Admin Console."));
        }
        addWide(buttonRow(secondary(
                "Back to Sign In", requiredNavigator::showSignIn)));
    }

    private static boolean usesLocalRecovery(AuthService authService) {
        return authService instanceof LocalAccountService
                && !authService.isSharedOnlineMode();
    }

    private void requestReset() {
        String enteredEmail = email.getText();
        requestButton.setEnabled(false);
        showStatus(localAccountService == null
                ? "Requesting password-reset instructions..."
                : "Loading the local recovery question...");
        new SwingWorker<PasswordRecoveryChallenge, Void>() {
            @Override
            protected PasswordRecoveryChallenge doInBackground() {
                if (localAccountService != null) {
                    return localAccountService
                            .getPasswordRecoveryChallenge(enteredEmail);
                }
                authService.forgotPassword(enteredEmail);
                return null;
            }

            @Override
            protected void done() {
                requestButton.setEnabled(true);
                try {
                    PasswordRecoveryChallenge challenge = get();
                    if (localAccountService == null) {
                        showSuccess("If the account is eligible, reset instructions were sent.");
                    } else {
                        question.setText("Question: " + challenge.question());
                        hint.setText("Hint: " + challenge.hint());
                        recoveryAnswer.setEnabled(true);
                        password.setEnabled(true);
                        confirmation.setEnabled(true);
                        resetButton.setEnabled(true);
                        showSuccess("Answer the question and choose a new password.");
                    }
                } catch (Exception exception) {
                    showFailure(workerFailure(exception));
                }
            }
        }.execute();
    }

    private void resetLocally() {
        char[] enteredAnswer = recoveryAnswer.getPassword();
        char[] enteredPassword = password.getPassword();
        char[] repeatedPassword = confirmation.getPassword();
        try {
            if (!Arrays.equals(enteredPassword, repeatedPassword)) {
                throw new AuthException("Passwords do not match.");
            }
            String enteredEmail = email.getText();
            resetButton.setEnabled(false);
            recoveryAnswer.setText("");
            password.setText("");
            confirmation.setText("");
            showStatus("Resetting the local password...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        localAccountService.resetPasswordWithRecovery(
                                enteredEmail, enteredAnswer,
                                enteredPassword, repeatedPassword);
                        return null;
                    } finally {
                        clear(enteredAnswer);
                        clear(enteredPassword);
                        clear(repeatedPassword);
                    }
                }

                @Override
                protected void done() {
                    resetButton.setEnabled(true);
                    try {
                        get();
                        showSuccess(
                                "Password reset completed. Return to sign in.");
                    } catch (Exception exception) {
                        showFailure(workerFailure(exception));
                    }
                }
            }.execute();
        } catch (RuntimeException exception) {
            clear(enteredAnswer);
            clear(enteredPassword);
            clear(repeatedPassword);
            showFailure(exception);
        }
    }
}
