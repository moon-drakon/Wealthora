package com.spendwise.auth.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthProvider;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.LocalAccountService;
import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledComboBox;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

public final class SecuritySessionsDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a")
                    .withZone(ZoneId.systemDefault());

    private final AuthService authService;
    private final LocalAccountService localAccountService;
    private final UserSession session;
    private final Runnable sessionEnded;
    private final boolean passwordAlreadySet;
    private final SessionTableModel sessionModel = new SessionTableModel();
    private final JTable sessionTable = new JTable(sessionModel);
    private final JLabel sessionStatus = new JLabel(" ");
    private final JLabel passwordStatus = new JLabel(" ");
    private final JLabel recoveryStatus = new JLabel(" ");
    private final JPasswordField currentPassword = passwordField();
    private final JPasswordField newPassword = passwordField();
    private final JPasswordField confirmation = passwordField();
    private final JPasswordField recoveryCurrentPassword = passwordField();
    private final StyledComboBox<String> recoveryQuestion =
            new StyledComboBox<>(RecoveryQuestionOptions.VALUES);
    private final StyledTextField recoveryHint = new StyledTextField(
            "Recovery hint", 30);
    private final JPasswordField recoveryAnswer = passwordField();
    private final JPasswordField recoveryAnswerConfirmation = passwordField();
    private final JButton passwordButton;
    private final JButton recoveryButton;
    private final JButton refreshButton = new SecondaryButton("Refresh");
    private final JButton revokeButton = new SecondaryButton("Revoke Selected");
    private final JButton logoutAllButton = new SecondaryButton("Sign Out All");

    public SecuritySessionsDialog(
            Window owner,
            UserSession session,
            AuthService authService,
            Runnable sessionEnded) {
        super(owner, "Security and Sessions",
                Dialog.ModalityType.APPLICATION_MODAL);
        UserSession requiredSession = Objects.requireNonNull(session);
        this.session = requiredSession;
        this.authService = Objects.requireNonNull(authService);
        localAccountService = authService instanceof LocalAccountService local
                ? local : null;
        this.sessionEnded = Objects.requireNonNull(sessionEnded);
        passwordAlreadySet = requiredSession.getProvider()
                != AuthProvider.GOOGLE;
        passwordButton = new PrimaryButton(passwordAlreadySet
                ? "Change Password" : "Set Password");
        boolean recoveryConfigured = localAccountService != null
                && localAccountService.hasPasswordRecovery(requiredSession);
        recoveryButton = new PrimaryButton(recoveryConfigured
                ? "Update Recovery" : "Set Up Recovery");

        JPanel content = new JPanel(new BorderLayout(0, 16));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JLabel title = new JLabel("Security and Sessions");
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        content.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Password", passwordPanel());
        if (localAccountService != null) {
            tabs.addTab("Recovery", recoveryPanel(recoveryConfigured));
        }
        tabs.addTab("Sessions", sessionsPanel());
        content.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        SecondaryButton close = new SecondaryButton("Close");
        close.addActionListener(event -> dispose());
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);

        passwordButton.addActionListener(event -> updatePassword());
        recoveryButton.addActionListener(event -> updateRecovery());
        refreshButton.addActionListener(event -> loadSessions());
        revokeButton.addActionListener(event -> revokeSelectedSession());
        logoutAllButton.addActionListener(event -> logoutAllSessions());
        sessionTable.getSelectionModel().addListSelectionListener(event ->
                revokeButton.setEnabled(sessionTable.getSelectedRow() >= 0));

        setContentPane(content);
        setSize(780, 620);
        setMinimumSize(new java.awt.Dimension(680, 520));
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(this::loadSessions);
    }

    private JPanel passwordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 12, 12, 12));
        int row = 0;
        if (passwordAlreadySet) {
            addPasswordField(panel, row, "Current Password", currentPassword);
            row += 2;
        }
        addPasswordField(panel, row, "New Password", newPassword);
        row += 2;
        addPasswordField(panel, row, "Confirm New Password", confirmation);
        row += 2;

        JLabel passwordPolicy = new JLabel(
                "6-128 characters; English letter and number; no outer spaces");
        passwordPolicy.setFont(AppFonts.caption());
        AppTheme.mark(passwordPolicy, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(passwordPolicy, constraints(row++));

        GridBagConstraints buttonConstraints = constraints(row++);
        buttonConstraints.insets = new Insets(8, 0, 6, 0);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(passwordButton);
        panel.add(buttons, buttonConstraints);

        passwordStatus.setFont(AppFonts.caption());
        AppTheme.mark(passwordStatus, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(passwordStatus, constraints(row));
        return panel;
    }

    private JPanel sessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 8, 8, 8));
        sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionTable.setRowHeight(30);
        sessionTable.setFillsViewportHeight(true);
        sessionTable.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(sessionTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        revokeButton.setEnabled(false);
        buttons.add(refreshButton);
        buttons.add(revokeButton);
        buttons.add(logoutAllButton);
        actions.add(buttons, BorderLayout.WEST);
        sessionStatus.setFont(AppFonts.caption());
        AppTheme.mark(sessionStatus, AppTheme.SECONDARY_TEXT_ROLE);
        actions.add(sessionStatus, BorderLayout.SOUTH);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel recoveryPanel(boolean configured) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 12, 12, 12));
        int row = 0;
        JLabel explanation = new JLabel(configured
                ? "Recovery is configured. Confirm your password to replace it."
                : "Add recovery now so this existing account can use Forgot Password.");
        explanation.setFont(AppFonts.body());
        AppTheme.mark(explanation, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(explanation, constraints(row++));
        addFormField(panel, row, "Current Password", recoveryCurrentPassword);
        row += 2;
        addFormField(panel, row, "Recovery Question", recoveryQuestion);
        row += 2;
        addFormField(panel, row, "Safe Hint", recoveryHint);
        row += 2;
        addFormField(panel, row, "Recovery Answer", recoveryAnswer);
        row += 2;
        addFormField(panel, row, "Confirm Recovery Answer",
                recoveryAnswerConfirmation);
        row += 2;
        JLabel privacy = new JLabel(
                "The hint must not reveal the answer. Only a protected answer hash is stored.");
        privacy.setFont(AppFonts.caption());
        AppTheme.mark(privacy, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(privacy, constraints(row++));
        GridBagConstraints buttonConstraints = constraints(row++);
        buttonConstraints.insets = new Insets(8, 0, 6, 0);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(recoveryButton);
        panel.add(buttons, buttonConstraints);
        recoveryStatus.setFont(AppFonts.caption());
        AppTheme.mark(recoveryStatus, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(recoveryStatus, constraints(row));
        return panel;
    }

    private void updatePassword() {
        char[] current = currentPassword.getPassword();
        char[] next = newPassword.getPassword();
        char[] repeated = confirmation.getPassword();
        if (!Arrays.equals(next, repeated)) {
            clear(current, next, repeated);
            showPasswordFailure("Passwords do not match.");
            return;
        }
        passwordButton.setEnabled(false);
        currentPassword.setText("");
        newPassword.setText("");
        confirmation.setText("");
        showPasswordStatus(passwordAlreadySet
                ? "Changing password securely..."
                : "Setting password securely...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    if (passwordAlreadySet) {
                        authService.changePassword(current, next);
                    } else {
                        authService.setPassword(next);
                    }
                    return null;
                } finally {
                    clear(current, next, repeated);
                }
            }

            @Override
            protected void done() {
                passwordButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(
                            SecuritySessionsDialog.this,
                            "Password updated. Sign in again on this device.",
                            "Password Updated", JOptionPane.INFORMATION_MESSAGE);
                    finishCurrentSession();
                } catch (Exception exception) {
                    showPasswordFailure(message(exception));
                }
            }
        }.execute();
    }

    private void updateRecovery() {
        char[] current = recoveryCurrentPassword.getPassword();
        char[] answer = recoveryAnswer.getPassword();
        char[] repeated = recoveryAnswerConfirmation.getPassword();
        if (!Arrays.equals(answer, repeated)) {
            clear(current, answer, repeated);
            showRecoveryFailure(
                    "Recovery answer confirmation does not match.");
            return;
        }
        String question = (String) recoveryQuestion.getSelectedItem();
        String hint = recoveryHint.getText();
        recoveryButton.setEnabled(false);
        recoveryCurrentPassword.setText("");
        recoveryAnswer.setText("");
        recoveryAnswerConfirmation.setText("");
        showRecoveryStatus("Protecting the recovery answer...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    localAccountService.updatePasswordRecovery(
                            session, current, question, hint,
                            answer, repeated);
                    return null;
                } finally {
                    clear(current, answer, repeated);
                }
            }

            @Override
            protected void done() {
                recoveryButton.setEnabled(true);
                try {
                    get();
                    recoveryButton.setText("Update Recovery");
                    showRecoverySuccess(
                            "Recovery is configured for Forgot Password.");
                } catch (Exception exception) {
                    showRecoveryFailure(message(exception));
                }
            }
        }.execute();
    }

    private void loadSessions() {
        setSessionButtonsEnabled(false);
        showSessionStatus("Loading active sessions...");
        new SwingWorker<List<AccountSession>, Void>() {
            @Override
            protected List<AccountSession> doInBackground() {
                return authService.listSessions();
            }

            @Override
            protected void done() {
                setSessionButtonsEnabled(true);
                try {
                    List<AccountSession> sessions = get();
                    sessionModel.setSessions(sessions);
                    showSessionStatus(sessions.isEmpty()
                            ? "No active sessions found."
                            : sessions.size() + " active session(s).");
                } catch (Exception exception) {
                    sessionModel.setSessions(List.of());
                    showSessionFailure(message(exception));
                }
            }
        }.execute();
    }

    private void revokeSelectedSession() {
        AccountSession selected = sessionModel.sessionAt(
                sessionTable.getSelectedRow());
        if (selected == null) return;
        if (JOptionPane.showConfirmDialog(this,
                "Revoke the selected session?",
                "Revoke Session", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        setSessionButtonsEnabled(false);
        showSessionStatus("Revoking selected session...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                authService.revokeSession(selected);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (selected.currentSession()) {
                        finishCurrentSession();
                    } else {
                        loadSessions();
                    }
                } catch (Exception exception) {
                    setSessionButtonsEnabled(true);
                    showSessionFailure(message(exception));
                }
            }
        }.execute();
    }

    private void logoutAllSessions() {
        if (JOptionPane.showConfirmDialog(this,
                "Sign out every active session for this account?",
                "Sign Out All Sessions", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        setSessionButtonsEnabled(false);
        showSessionStatus("Signing out all sessions...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                authService.logoutAll();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    finishCurrentSession();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(
                            SecuritySessionsDialog.this,
                            message(exception)
                            + " Local sign-in was still cleared.",
                            "Remote Sign Out Warning",
                            JOptionPane.WARNING_MESSAGE);
                    finishCurrentSession();
                }
            }
        }.execute();
    }

    private void finishCurrentSession() {
        dispose();
        sessionEnded.run();
    }

    private void setSessionButtonsEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        logoutAllButton.setEnabled(enabled);
        revokeButton.setEnabled(enabled
                && sessionTable.getSelectedRow() >= 0);
    }

    private void showPasswordStatus(String message) {
        passwordStatus.setForeground(AppColors.secondaryText());
        passwordStatus.setText(message);
    }

    private void showPasswordFailure(String message) {
        passwordStatus.setForeground(AppColors.expense());
        passwordStatus.setText(message);
    }

    private void showRecoveryStatus(String message) {
        recoveryStatus.setForeground(AppColors.secondaryText());
        recoveryStatus.setText(message);
    }

    private void showRecoverySuccess(String message) {
        recoveryStatus.setForeground(AppColors.income());
        recoveryStatus.setText(message);
    }

    private void showRecoveryFailure(String message) {
        recoveryStatus.setForeground(AppColors.expense());
        recoveryStatus.setText(message);
    }

    private void showSessionStatus(String message) {
        sessionStatus.setForeground(AppColors.secondaryText());
        sessionStatus.setText(message);
    }

    private void showSessionFailure(String message) {
        sessionStatus.setForeground(AppColors.expense());
        sessionStatus.setText(message);
    }

    private static String message(Exception exception) {
        Throwable cause = exception
                instanceof java.util.concurrent.ExecutionException
                ? exception.getCause() : exception;
        String value = cause == null ? null : cause.getMessage();
        return value == null || value.isBlank()
                ? "The security request could not be completed." : value;
    }

    private static void addPasswordField(
            JPanel panel, int row, String labelText, JPasswordField field) {
        addFormField(panel, row, labelText, field);
    }

    private static void addFormField(
            JPanel panel, int row, String labelText, Component field) {
        GridBagConstraints labelConstraints = constraints(row);
        labelConstraints.insets = new Insets(3, 0, 4, 0);
        JLabel label = new JLabel(labelText);
        label.setFont(AppFonts.button());
        label.setLabelFor(field);
        AppTheme.mark(label, AppTheme.PRIMARY_TEXT_ROLE);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = constraints(row + 1);
        fieldConstraints.insets = new Insets(0, 0, 10, 0);
        panel.add(field, fieldConstraints);
    }

    private static JPasswordField passwordField() {
        JPasswordField field = new JPasswordField(30);
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; margin: 6,9,6,9; showRevealButton: true");
        return field;
    }

    private static GridBagConstraints constraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static void clear(char[]... values) {
        for (char[] value : values) {
            if (value != null) Arrays.fill(value, '\0');
        }
    }

    private static final class SessionTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
            "Device", "Signed In", "Access Expires", "Current"
        };
        private final List<AccountSession> sessions = new ArrayList<>();

        void setSessions(List<AccountSession> values) {
            sessions.clear();
            sessions.addAll(values);
            fireTableDataChanged();
        }

        AccountSession sessionAt(int row) {
            return row < 0 || row >= sessions.size()
                    ? null : sessions.get(row);
        }

        @Override
        public int getRowCount() {
            return sessions.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            AccountSession session = sessions.get(row);
            return switch (column) {
                case 0 -> session.deviceLabel();
                case 1 -> format(session.createdAt());
                case 2 -> session.accessExpiresAt() == null
                        ? "Until sign out" : format(session.accessExpiresAt());
                case 3 -> session.currentSession() ? "Yes" : "";
                default -> "";
            };
        }

        private static String format(Instant value) {
            return DATE_TIME.format(value);
        }
    }
}
