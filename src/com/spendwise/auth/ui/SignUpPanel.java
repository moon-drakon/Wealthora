package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.StyledTextField;
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
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField fullName = textField("Full name");
    private final StyledTextField email = textField("NSU email");
    private final StyledTextField studentId = textField("Student ID (optional)");
    private final JPasswordField password = passwordField("Password");
    private final JPasswordField confirmation =
            passwordField("Confirm password");
    private final JCheckBox terms = new JCheckBox(
            "I accept the Terms and Privacy Notice");
    private final JLabel passwordStrength = new JLabel(
            "Password strength: enter at least 12 characters");
    private final JButton createButton;

    public SignUpPanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Create Account",
                "Register with an official NSU email or continue with Google.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        addWide(primary("Continue with Google", this::continueWithGoogle));
        addWide(helperLabel(
                "Google registration requires real browser OAuth configuration; it never simulates success."));
        addWide(orDivider());
        addWide(sectionHeading(
                "NSU Email Registration", AppBrand.NSU_EMAIL_SUBTITLE));
        addField("Full Name", fullName);
        addField("NSU Email", email);
        addField("Student ID (optional)", studentId);
        addField("Password", password);
        addWide(passwordStrength);
        addField("Confirm Password", confirmation);
        terms.setOpaque(false);
        terms.setToolTipText(
                "Required before the server creates a pending account.");
        addWide(terms);
        createButton = primary("Create Account", this::createAccount);
        addWide(buttonRow(
                createButton,
                secondary("Back to Sign In", navigator::showSignIn)));
        addWide(helperLabel(
                "Only exact @northsouth.edu addresses are accepted. A verification email is required, followed by administrator approval under the current rollout policy."));
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
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            if (!terms.isSelected()) {
                throw new AuthException(
                        "Accept the Terms and Privacy Notice to create an account.");
            }
            String enteredName = fullName.getText();
            String enteredEmail = email.getText();
            String enteredStudentId = studentId.getText();
            createButton.setEnabled(false);
            showStatus("Creating a protected pending account...");
            password.setText("");
            confirmation.setText("");
            new SwingWorker<AuthenticatedUser, Void>() {
                @Override
                protected AuthenticatedUser doInBackground() {
                    try {
                        return authService.registerWithNsuEmail(
                                enteredName, enteredEmail,
                                enteredStudentId, entered, true);
                    } finally {
                        clear(entered);
                        clear(repeated);
                    }
                }

                @Override
                protected void done() {
                    createButton.setEnabled(true);
                    try {
                        AuthenticatedUser account = get();
                        if (account.isEmailVerified()) {
                            showSuccess(
                                    "Account verified. Return to sign in.");
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
            password.setText("");
            confirmation.setText("");
        }
    }

    private void continueWithGoogle() {
        try {
            UserSession session = authService.continueWithGoogle();
            sessionManager.startSession(session);
            navigator.showAuthenticatedProfile(session);
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }

    private void updatePasswordStrength() {
        char[] value = password.getPassword();
        try {
            int score = 0;
            if (value.length >= 12) score++;
            if (value.length >= 16) score++;
            boolean upper = false, lower = false, digit = false, symbol = false;
            for (char character : value) {
                upper |= Character.isUpperCase(character);
                lower |= Character.isLowerCase(character);
                digit |= Character.isDigit(character);
                symbol |= !Character.isLetterOrDigit(character);
            }
            if (upper && lower) score++;
            if (digit && symbol) score++;
            String text = score < 2 ? "Weak" : score < 4 ? "Good" : "Strong";
            passwordStrength.setText("Password strength: " + text);
            passwordStrength.setForeground(score < 2
                    ? AppColors.expense() : score < 4
                            ? AppColors.warning() : AppColors.income());
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
