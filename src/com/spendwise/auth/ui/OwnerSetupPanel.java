package com.spendwise.auth.ui;

import com.spendwise.auth.OwnerSetupService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.component.StyledComboBox;
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
    private final StyledComboBox<String> recoveryQuestion =
            new StyledComboBox<>(RecoveryQuestionOptions.VALUES);
    private final StyledTextField recoveryHint = textField(
            "A helpful hint that does not reveal the answer");
    private final JPasswordField recoveryAnswer =
            passwordField("Recovery answer");
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
        addWide(sectionHeading("OWNER Recovery",
                "Choose an answer you can remember. Only its protected hash is stored."));
        addField("Recovery question", recoveryQuestion);
        addField("Recovery hint", recoveryHint);
        addField("Recovery answer", recoveryAnswer);
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
        char[] enteredRecoveryAnswer = recoveryAnswer.getPassword();
        try {
            UserSession session = ownerSetupService.createFirstOwner(
                    fullName.getText(), email.getText(), enteredPassword,
                    enteredConfirmation,
                    (String) recoveryQuestion.getSelectedItem(),
                    recoveryHint.getText(), enteredRecoveryAnswer);
            sessionManager.startSession(session);
            navigator.showAuthenticatedProfile(session);
        } catch (RuntimeException exception) {
            showFailure(exception);
        } finally {
            clear(enteredPassword);
            clear(enteredConfirmation);
            clear(enteredRecoveryAnswer);
            password.setText("");
            confirmation.setText("");
            recoveryAnswer.setText("");
        }
    }
}
