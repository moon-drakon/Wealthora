package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.LocalAccountService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.component.StyledComboBox;
import com.spendwise.ui.theme.AppColors;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class SignUpPanel extends AuthFormPanel {

    private final AuthService authService;
    private final LocalAccountService localAccountService;
    private final SessionManager sessionManager;
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
    private final JCheckBox terms = new JCheckBox(
            "I accept the Terms and Privacy Notice");
    private final JLabel passwordStrength = new JLabel(
            "Password: 8-128 characters with an English letter and number");
    private final JButton createButton;
    private final JButton googleButton;
    private final boolean sharedOnline;

    public SignUpPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Create Account", authService.isSharedOnlineMode()
                ? "Create a private account for the shared-online workspace."
                : authService instanceof LocalAccountService
                        ? "Create a private account for this computer. Each user receives a separate finance workspace."
                        : "Register with an official NSU email.");
        this.authService = Objects.requireNonNull(authService);
        sharedOnline = authService.isSharedOnlineMode();
        this.localAccountService = !sharedOnline
                && authService instanceof LocalAccountService local
                ? local : null;
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        googleButton = localAccountService == null && !sharedOnline
                ? primary("Continue with Google", this::continueWithGoogle)
                : null;
        if (googleButton != null) {
            addWide(googleButton);
            addWide(helperLabel(
                    "Google registration requires real browser OAuth configuration; it never simulates success."));
            addWide(orDivider());
        }
        addWide(sectionHeading(
                sharedOnline ? "Shared Online Registration"
                        : localAccountService == null
                                ? "NSU Email Registration"
                                : "Local User Account",
                localAccountService == null ? AppBrand.NSU_EMAIL_SUBTITLE
                        : "Official NSU email · protected password · private local data"));
        addField("Full Name", fullName);
        addField("NSU Email", email);
        addField("Student ID (optional)", studentId);
        addField("Password", password);
        addWide(passwordStrength);
        addField("Confirm Password", confirmation);
        if (localAccountService != null) {
            addWide(sectionHeading("Password Recovery",
                    "The answer is protected like a password and is never displayed."));
            addField("Recovery Question", recoveryQuestion);
            addField("Recovery Hint", recoveryHint);
            addField("Recovery Answer", recoveryAnswer);
        } else {
            terms.setOpaque(false);
            terms.setToolTipText(
                    "Required before the server creates a pending account.");
            addWide(terms);
        }
        createButton = primary("Create Account", this::createAccount);
        addWide(buttonRow(
                createButton,
                secondary("Back to Sign In", navigator::showSignIn)));
        addWide(helperLabel(
                sharedOnline
                        ? "Only exact @northsouth.edu addresses are accepted. New accounts receive the USER role and cannot access anyone else's records."
                        : localAccountService == null
                        ? "Only exact @northsouth.edu addresses are accepted. A verification email is required; administrator approval is optional and disabled by default."
                        : "Only exact @northsouth.edu addresses are accepted. This offline account is activated immediately on this computer."));
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
            if (localAccountService == null && !terms.isSelected()) {
                throw new AuthException(
                        "Accept the Terms and Privacy Notice to create an account.");
            }
            String enteredName = fullName.getText();
            String enteredEmail = email.getText();
            String enteredStudentId = studentId.getText();
            String selectedRecoveryQuestion =
                    (String) recoveryQuestion.getSelectedItem();
            String enteredRecoveryHint = recoveryHint.getText();
            createButton.setEnabled(false);
            showStatus(sharedOnline
                    ? "Creating your shared-online account..."
                    : localAccountService == null
                            ? "Creating a protected pending account..."
                            : "Creating the protected local account...");
            password.setText("");
            confirmation.setText("");
            recoveryAnswer.setText("");
            new SwingWorker<AuthenticatedUser, Void>() {
                @Override
                protected AuthenticatedUser doInBackground() {
                    try {
                        if (localAccountService != null) {
                            return localAccountService.registerLocalAccount(
                                    enteredName, enteredEmail,
                                    enteredStudentId, entered, repeated,
                                    selectedRecoveryQuestion,
                                    enteredRecoveryHint,
                                    enteredRecoveryAnswer);
                        }
                        return authService.registerWithNsuEmail(
                                enteredName, enteredEmail,
                                enteredStudentId, entered, true);
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
                        AuthenticatedUser account = get();
                        if (localAccountService != null) {
                            showSuccess(
                                    "Account created. Use Back to Sign In when ready.");
                        } else if (account.isEmailVerified()) {
                            showSuccess(
                                    "Account created. Return to sign in.");
                        } else {
                            navigator.showVerification(account.getEmail());
                        }
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

    private void continueWithGoogle() {
        googleButton.setEnabled(false);
        showStatus("Opening secure Google Sign-In in your browser...");
        new SwingWorker<UserSession, Void>() {
            @Override
            protected UserSession doInBackground() {
                return authService.continueWithGoogle();
            }

            @Override
            protected void done() {
                googleButton.setEnabled(true);
                try {
                    UserSession session = get();
                    sessionManager.startSession(session);
                    navigator.showAuthenticatedProfile(session);
                } catch (Exception exception) {
                    showFailure(authenticationFailure(exception));
                }
            }
        }.execute();
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
                    : "Password: 8-128 characters with an English letter and number; no outer spaces");
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
