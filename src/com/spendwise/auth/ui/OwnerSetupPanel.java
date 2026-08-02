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
                "Create the single primary OWNER account. This identity "
                        + "protects administrator access and receives any "
                        + "existing local finance data without deleting the originals.");
        this.ownerSetupService = Objects.requireNonNull(ownerSetupService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        email.setEditable(false);
        email.setFocusable(false);
        addWide(sectionHeading("Primary OWNER",
                "The email is locked to the APP_OWNER_EMAIL environment setting."));
        addField("Full name", fullName);
        addField("OWNER email", email);
        addField("Password", password);
        addField("Confirm password", confirmation);
        addWide(helperLabel(
                "Use at least 12 characters with uppercase, lowercase, "
                        + "a number, and a symbol. Passwords are stored only as BCrypt hashes."));
        createButton = primary("Create OWNER and open My Finance",
                this::createOwner);
        addWide(createButton);
        loadConfiguration();
    }

    public void reload() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            email.setText(ownerSetupService.getConfiguredOwnerEmail());
            createButton.setEnabled(true);
        } catch (RuntimeException exception) {
            email.setText("APP_OWNER_EMAIL is not configured");
            createButton.setEnabled(false);
            showFailure(exception);
        }
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
