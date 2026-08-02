package com.spendwise.auth.ui;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.StyledTextField;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.JPasswordField;

public final class SignUpPanel extends AuthFormPanel {

    private final AuthService authService;
    private final AuthNavigator navigator;
    private final StyledTextField displayName = textField("Display name");
    private final StyledTextField email = textField("NSU email");
    private final JPasswordField password = passwordField("Password");
    private final JPasswordField confirmation =
            passwordField("Confirm password");

    public SignUpPanel(AuthService authService, AuthNavigator navigator) {
        super("Create account", "Prepare an NSU-only SpendWise account. "
                + "Email verification requires the configured backend.");
        this.authService = Objects.requireNonNull(authService);
        this.navigator = Objects.requireNonNull(navigator);
        addWide(policyLabel());
        addField("Display name", displayName);
        addField("NSU email", email);
        addField("Password", password);
        addField("Confirm password", confirmation);
        addWide(buttonRow(
                primary("Create account", this::createAccount),
                secondary("Back to sign in", navigator::showSignIn)));
    }

    private void createAccount() {
        char[] entered = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) {
                throw new AuthException("Passwords do not match.");
            }
            UserSession account = authService.createAccount(
                    displayName.getText(), email.getText(), entered);
            if (account.isVerified()) {
                showSuccess("Account verified. Return to sign in.");
            } else {
                navigator.showVerification(account.getEmail());
            }
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
