package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.otp.EmailOtpAccountService;
import com.spendwise.auth.otp.EmailOtpChallenge;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.component.StyledComboBox;
import com.spendwise.ui.theme.AppColors;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class SignUpPanel extends AuthFormPanel {

    private final EmailOtpAccountService emailOtpAccountService;
    private final AuthNavigator navigator;
    private final StyledTextField fullName = textField("Full name");
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField studentId = textField("Student ID (optional)");
    private final JPasswordField password = passwordField("Password");
    private final JPasswordField confirmation =
            passwordField("Confirm password");
    private final StyledComboBox<String> recoveryQuestion =
            new StyledComboBox<>(RecoveryQuestionOptions.VALUES);
    private final StyledTextField recoveryHint = textField(
            "A helpful hint that does not reveal the answer");
    private final JPasswordField recoveryAnswer =
            passwordField("Recovery answer");
    private final JLabel passwordStrength = new JLabel(
            "Password: 6-128 characters with an English letter and number");
    private final JButton createButton;

    public SignUpPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Create Account",
                "Verify an official NSU email before creating a private local account.");
        Objects.requireNonNull(authService);
        this.emailOtpAccountService = authService
                instanceof EmailOtpAccountService otp ? otp : null;
        Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        addWide(sectionHeading(
                "Local User Registration",
                "Official NSU email · email OTP · protected password · private project-local data"));
        addField("Full Name", fullName);
        addField("NSU Email", email);
        addField("Student ID (optional)", studentId);
        addField("Password", password);
        addWide(passwordStrength);
        addField("Confirm Password", confirmation);
        addWide(sectionHeading("Offline Recovery",
                "The answer is protected like a password and remains available without internet."));
        addField("Recovery Question", recoveryQuestion);
        addField("Recovery Hint", recoveryHint);
        addField("Recovery Answer", recoveryAnswer);
        createButton = primary("Send Verification Code", this::createAccount);
        addWide(buttonRow(
                createButton,
                secondary("Back to Sign In", navigator::showSignIn)));
        addWide(helperLabel(
                "Only explicit OTP send and verify actions use the internet. No account is created before successful verification."));
        password.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) {
                updatePasswordStrength();
            }
            @Override public void removeUpdate(DocumentEvent event) {
                updatePasswordStrength();
            }
            @Override public void changedUpdate(DocumentEvent event) {
                updatePasswordStrength();
            }
        });
    }

    private void createAccount() {
        char[] entered = password.getPassword();
        char[] repeated = confirmation.getPassword();
        char[] enteredRecoveryAnswer = recoveryAnswer.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            if (emailOtpAccountService == null) {
                throw new AuthException(
                        "Local email-OTP registration is unavailable.");
            }
            String enteredName = fullName.getText();
            String enteredEmail = email.getText();
            String enteredStudentId = studentId.getText();
            String selectedRecoveryQuestion =
                    (String) recoveryQuestion.getSelectedItem();
            String enteredRecoveryHint = recoveryHint.getText();
            createButton.setEnabled(false);
            showStatus("Requesting a registration verification code...");
            password.setText("");
            confirmation.setText("");
            recoveryAnswer.setText("");
            new SwingWorker<EmailOtpChallenge, Void>() {
                @Override
                protected EmailOtpChallenge doInBackground() {
                    try {
                        return emailOtpAccountService.beginRegistration(
                                enteredName, enteredEmail,
                                enteredStudentId, entered, repeated,
                                selectedRecoveryQuestion,
                                enteredRecoveryHint,
                                enteredRecoveryAnswer);
                    } finally {
                        clear(entered);
                        clear(repeated);
                        clear(enteredRecoveryAnswer);
                    }
                }

                @Override
                protected void done() {
                    createButton.setEnabled(true);
                    try {
                        navigator.showRegistrationVerification(get());
                    } catch (Exception exception) {
                        showFailure(authenticationFailure(exception));
                    }
                }
            }.execute();
        } catch (RuntimeException exception) {
            showFailure(exception);
            clear(entered);
            clear(repeated);
            clear(enteredRecoveryAnswer);
            password.setText("");
            confirmation.setText("");
            recoveryAnswer.setText("");
        }
    }

    private void updatePasswordStrength() {
        char[] value = password.getPassword();
        try {
            boolean englishLetter = false;
            boolean digit = false;
            for (char character : value) {
                englishLetter |= (character >= 'A' && character <= 'Z')
                        || (character >= 'a' && character <= 'z');
                digit |= character >= '0' && character <= '9';
            }
            boolean outerSpace = value.length > 0
                    && (Character.isWhitespace(value[0])
                    || Character.isWhitespace(value[value.length - 1]));
            boolean valid = value.length >= 8 && value.length <= 128
                    && englishLetter && digit && !outerSpace;
            passwordStrength.setText(valid
                    ? "Password meets the required policy"
                    : "Password: 6-128 characters with an English letter and number; no outer spaces");
            passwordStrength.setForeground(valid
                    ? AppColors.income() : AppColors.warning());
        } finally {
            clear(value);
        }
    }

    private static RuntimeException authenticationFailure(Exception exception) {
        Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                ? exception.getCause() : exception;
        return cause instanceof RuntimeException runtime
                ? runtime : new AuthException(
                        "Account creation could not be completed.", cause);
    }
}
