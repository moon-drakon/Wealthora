package com.spendwise.auth.ui;

import com.spendwise.auth.OwnerSetupService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.StyledTextField;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPasswordField;

public final class OwnerSetupPanel extends AuthFormPanel {

    private final OwnerSetupService ownerSetupService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final StyledTextField fullName = textField("Owner full name");
    private final StyledTextField email = textField("Configured owner email");
    private final JPasswordField password = passwordField("Owner password");
    private final JPasswordField confirmation =
            passwordField("Confirm owner password");
    private final JButton createButton;

    public OwnerSetupPanel(
            OwnerSetupService ownerSetupService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Secure first-run setup",
                "Create the primary local account for this copy of Wealthora.");
        this.ownerSetupService = Objects.requireNonNull(ownerSetupService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        addWide(sectionHeading("Primary OWNER",
                "Use an NSU email address. The account is stored on this computer."));
        addField("Full name", fullName);
        addField("OWNER email", email);
        addField("Password", password);
        addField("Confirm password", confirmation);
        addWide(helperLabel(
                "Use 8-128 characters with at least one English letter and one number, with no outer spaces. Passwords are stored only as protected BCrypt hashes."));
        createButton = primary("Create OWNER and open My Finance",
                this::createOwner);
        addWide(createButton);
        loadConfiguration();
    }

    public void reload() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        String configuredEmail = ownerSetupService.getConfiguredOwnerEmail();
        boolean fixedEmail = configuredEmail != null
                && !configuredEmail.isBlank();
        email.setText(fixedEmail ? configuredEmail : "");
        email.setEditable(!fixedEmail);
        email.setFocusable(!fixedEmail);
        createButton.setEnabled(true);
    }

    private void createOwner() {
        char[] enteredPassword = password.getPassword();
        char[] enteredConfirmation = confirmation.getPassword();
        try {
            UserSession session = ownerSetupService.createFirstOwner(
                    fullName.getText(), email.getText(), enteredPassword,
                    enteredConfirmation);
            sessionManager.startSession(session);
            navigator.showAuthenticatedProfile(session);
        } catch (RuntimeException exception) {
            showFailure(exception);
        } finally {
            clear(enteredPassword);
            clear(enteredConfirmation);
            password.setText("");
            confirmation.setText("");
        }
    }
}
