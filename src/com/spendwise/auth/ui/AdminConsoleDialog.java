package com.spendwise.auth.ui;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.admin.AdminApplicationSettings;
import com.spendwise.auth.admin.AdminOverview;
import com.spendwise.auth.admin.AdminSecurityStatus;
import com.spendwise.auth.admin.AdminService;
import com.spendwise.auth.admin.DatabaseHealthStatus;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.ui.component.ConfirmationDialogs;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

public final class AdminConsoleDialog extends JDialog {

    private final AdminService adminService;
    private final UserSession session;
    private final Runnable createBackup;
    private final Runnable restoreBackup;
    private final AdminUserTableModel userModel = new AdminUserTableModel();
    private final AdminUserTableModel pendingModel = new AdminUserTableModel();
    private final AdminUserTableModel verificationModel = new AdminUserTableModel();
    private final AdminUserTableModel administratorModel = new AdminUserTableModel();
    private final AuditTableModel auditModel = new AuditTableModel();
    private final StyledTable userTable = new StyledTable(userModel);
    private final StyledTable pendingTable = new StyledTable(pendingModel);
    private final StyledTable verificationTable = new StyledTable(verificationModel);
    private final StyledTable administratorTable = new StyledTable(administratorModel);
    private final StyledTextField administratorSearch =
            new StyledTextField("Search active verified users", 28);
    private final List<AuthenticatedUser> cachedUsers = new ArrayList<>();
    private final JLabel totalUsers = metric("0");
    private final JLabel activeUsers = metric("0");
    private final JLabel pendingApproval = metric("0");
    private final JLabel pendingVerification = metric("0");
    private final JLabel suspendedUsers = metric("0");
    private final JLabel disabledUsers = metric("0");
    private final JLabel administrators = metric("0");
    private final JLabel owners = metric("0");
    private final JLabel standardUsers = metric("0");
    private final JLabel failedAttempts = metric("0");
    private final JLabel lastBackup = metric("None");
    private final JLabel storageStatus = metric("Checking");
    private final JLabel passwordPolicy = valueLabel();
    private final JLabel tokenExpiry = valueLabel();
    private final JLabel refreshExpiry = valueLabel();
    private final JLabel lockoutPolicy = valueLabel();
    private final JLabel verificationPolicy = valueLabel();
    private final JLabel resetExpiry = valueLabel();
    private final JLabel databaseStatus = valueLabel();
    private final JLabel databaseProduct = valueLabel();
    private final JLabel databaseMigrations = valueLabel();
    private final JLabel databaseUsers = valueLabel();
    private final JLabel databaseSessions = valueLabel();
    private final JCheckBox approvalRequired = new JCheckBox(
            "Require administrator approval after email verification");
    private final SecondaryButton saveSettings = new SecondaryButton("Save");
    private final SecondaryButton refreshButton = new SecondaryButton("Refresh");
    private boolean busy;

    public AdminConsoleDialog(
            Window owner,
            AdminService adminService,
            UserSession session,
            Runnable createBackup,
            Runnable restoreBackup) {
        super(owner, "Admin Console", Dialog.ModalityType.APPLICATION_MODAL);
        this.adminService = Objects.requireNonNull(adminService);
        this.session = Objects.requireNonNull(session);
        this.createBackup = Objects.requireNonNull(createBackup);
        this.restoreBackup = Objects.requireNonNull(restoreBackup);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        content.add(heading(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.addTab("Overview", overviewPanel());
        tabs.addTab("Users", usersPanel());
        tabs.addTab("Pending Registrations", pendingPanel());
        tabs.addTab("Verification", verificationPanel());
        tabs.addTab("Administrators", administratorsPanel());
        tabs.addTab("Audit Logs", tablePanel(new StyledTable(auditModel)));
        tabs.addTab("Security", securityPanel());
        tabs.addTab("Application Settings", settingsPanel());
        tabs.addTab("Backup and Restore", backupPanel());
        tabs.addTab("Database Health", databasePanel());
        content.add(tabs, BorderLayout.CENTER);
        content.add(footer(), BorderLayout.SOUTH);

        setContentPane(content);
        setSize(1160, 760);
        setMinimumSize(new java.awt.Dimension(920, 620));
        setLocationRelativeTo(owner);
        refreshAll();
    }

    private JPanel heading() {
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Admin Console");
        title.setFont(AppFonts.pageTitle());
        JLabel identity = new JLabel(session.getDisplayName() + " | "
                + session.getUser().getHighestRole().name());
        identity.setFont(AppFonts.caption());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        AppTheme.mark(identity, AppTheme.SECONDARY_TEXT_ROLE);
        heading.add(title, BorderLayout.WEST);
        heading.add(identity, BorderLayout.EAST);
        return heading;
    }

    private JPanel footer() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        refreshButton.addActionListener(event -> refreshAll());
        PrimaryButton close = new PrimaryButton("Back to My Finance");
        close.addActionListener(event -> dispose());
        footer.add(refreshButton);
        footer.add(close);
        return footer;
    }

    private JPanel overviewPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 4, 12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 4, 16, 4));
        panel.setOpaque(false);
        panel.add(metricCard("Total users", totalUsers));
        panel.add(metricCard("Active", activeUsers));
        panel.add(metricCard("Pending approval", pendingApproval));
        panel.add(metricCard("Pending verification", pendingVerification));
        panel.add(metricCard("Suspended", suspendedUsers));
        panel.add(metricCard("Disabled", disabledUsers));
        panel.add(metricCard("Primary owners", owners));
        panel.add(metricCard("Administrators", administrators));
        panel.add(metricCard("Standard users", standardUsers));
        panel.add(metricCard("Failed attempts", failedAttempts));
        panel.add(metricCard("Last backup", lastBackup));
        panel.add(metricCard("Storage status", storageStatus));
        return panel;
    }

    private JPanel usersPanel() {
        JPanel panel = tablePanel(userTable);
        JPanel actions = actionRow();
        SecondaryButton activate = new SecondaryButton("Activate");
        SecondaryButton suspend = new SecondaryButton("Suspend");
        SecondaryButton disable = new SecondaryButton("Disable");
        activate.addActionListener(event -> changeStatus(AccountStatus.ACTIVE));
        suspend.addActionListener(event -> changeStatus(AccountStatus.SUSPENDED));
        disable.addActionListener(event -> changeStatus(AccountStatus.DISABLED));
        actions.add(activate);
        actions.add(suspend);
        actions.add(disable);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel pendingPanel() {
        JPanel panel = tablePanel(pendingTable);
        JPanel actions = actionRow();
        PrimaryButton approve = new PrimaryButton("Approve");
        SecondaryButton reject = new SecondaryButton("Reject");
        approve.addActionListener(event -> reviewRegistration(true, pendingTable,
                pendingModel));
        reject.addActionListener(event -> reviewRegistration(false, pendingTable,
                pendingModel));
        actions.add(approve);
        actions.add(reject);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel verificationPanel() {
        JPanel panel = tablePanel(verificationTable);
        JPanel actions = actionRow();
        SecondaryButton reject = new SecondaryButton("Reject Registration");
        reject.setToolTipText(
                "Administrators cannot mark an email as verified.");
        reject.addActionListener(event -> reviewRegistration(false,
                verificationTable, verificationModel));
        actions.add(reject);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel administratorsPanel() {
        JPanel panel = tablePanel(administratorTable);
        JPanel searchArea = new JPanel(new BorderLayout(8, 0));
        searchArea.setOpaque(false);
        JLabel label = new JLabel("Find active verified user");
        label.setFont(AppFonts.button());
        searchArea.add(label, BorderLayout.WEST);
        searchArea.add(administratorSearch, BorderLayout.CENTER);
        administratorSearch.getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent event) {
                        filterAdministratorCandidates();
                    }
                    @Override public void removeUpdate(DocumentEvent event) {
                        filterAdministratorCandidates();
                    }
                    @Override public void changedUpdate(DocumentEvent event) {
                        filterAdministratorCandidates();
                    }
                });
        panel.add(searchArea, BorderLayout.NORTH);
        JPanel actions = actionRow();
        PrimaryButton grant = new PrimaryButton("Grant ADMIN");
        SecondaryButton revoke = new SecondaryButton("Revoke ADMIN");
        grant.setEnabled(session.isOwner());
        revoke.setEnabled(session.isOwner());
        String tooltip = session.isOwner()
                ? "Requires OWNER password confirmation and a reason."
                : "Only the OWNER can manage administrators.";
        grant.setToolTipText(tooltip);
        revoke.setToolTipText(tooltip);
        grant.addActionListener(event -> changeAdministrator(true));
        revoke.addActionListener(event -> changeAdministrator(false));
        actions.add(grant);
        actions.add(revoke);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel securityPanel() {
        JPanel panel = detailPanel(6);
        addDetail(panel, "Password policy", passwordPolicy);
        addDetail(panel, "Access token expiry", tokenExpiry);
        addDetail(panel, "Refresh token expiry", refreshExpiry);
        addDetail(panel, "Failed-login lockout", lockoutPolicy);
        addDetail(panel, "Verification policy", verificationPolicy);
        addDetail(panel, "Password reset expiry", resetExpiry);
        return panel;
    }

    private JPanel settingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 8, 22, 8));
        approvalRequired.setOpaque(false);
        approvalRequired.setFont(AppFonts.body());
        panel.add(approvalRequired, BorderLayout.NORTH);
        JPanel actions = actionRow();
        saveSettings.setEnabled(false);
        saveSettings.addActionListener(event -> updateSettings());
        actions.add(saveSettings);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel backupPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 22));
        panel.setOpaque(false);
        PrimaryButton backup = new PrimaryButton("Create Backup");
        SecondaryButton restore = new SecondaryButton("Restore Backup");
        backup.addActionListener(event -> runLocalDataAction(
                "Create backup", createBackup));
        restore.addActionListener(event -> runLocalDataAction(
                "Restore backup", restoreBackup));
        panel.add(backup);
        panel.add(restore);
        return panel;
    }

    private JPanel databasePanel() {
        JPanel panel = detailPanel(5);
        addDetail(panel, "Status", databaseStatus);
        addDetail(panel, "Database", databaseProduct);
        addDetail(panel, "Applied migrations", databaseMigrations);
        addDetail(panel, "Users", databaseUsers);
        addDetail(panel, "Active sessions", databaseSessions);
        return panel;
    }

    private void refreshAll() {
        if (busy) return;
        setBusy(true);
        new SwingWorker<AdminSnapshot, Void>() {
            @Override protected AdminSnapshot doInBackground() {
                return new AdminSnapshot(
                        adminService.listUsers(session),
                        adminService.listPendingRegistrations(session),
                        adminService.listPendingVerifications(session),
                        adminService.getAuditEvents(session),
                        adminService.getOverview(session),
                        adminService.getSecurityStatus(session),
                        adminService.getApplicationSettings(session),
                        adminService.getDatabaseHealth(session));
            }

            @Override protected void done() {
                setBusy(false);
                try {
                    applySnapshot(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure("Admin Console", exception);
                } catch (ExecutionException exception) {
                    showFailure("Admin Console", exception.getCause());
                }
            }
        }.execute();
    }

    private void applySnapshot(AdminSnapshot snapshot) {
        cachedUsers.clear();
        cachedUsers.addAll(snapshot.users());
        userModel.setRows(snapshot.users());
        pendingModel.setRows(snapshot.pendingRegistrations());
        verificationModel.setRows(snapshot.pendingVerifications());
        auditModel.setRows(snapshot.auditEvents());
        filterAdministratorCandidates();

        AdminOverview overview = snapshot.overview();
        totalUsers.setText(Integer.toString(overview.totalUsers()));
        activeUsers.setText(Integer.toString(overview.activeUsers()));
        pendingApproval.setText(Integer.toString(overview.pendingApproval()));
        pendingVerification.setText(Integer.toString(overview.pendingVerification()));
        suspendedUsers.setText(Integer.toString(overview.suspendedUsers()));
        disabledUsers.setText(Integer.toString(overview.disabledUsers()));
        owners.setText(Integer.toString(overview.owners()));
        administrators.setText(Integer.toString(overview.administrators()));
        standardUsers.setText(Integer.toString(overview.standardUsers()));
        failedAttempts.setText(Integer.toString(overview.failedLoginAttempts()));
        lastBackup.setText(overview.lastBackup());
        lastBackup.setToolTipText(overview.lastBackup());
        storageStatus.setText(overview.storageStatus());

        AdminSecurityStatus security = snapshot.security();
        passwordPolicy.setText(security.passwordPolicy());
        tokenExpiry.setText(security.accessTokenExpiry());
        refreshExpiry.setText(security.refreshTokenExpiry());
        lockoutPolicy.setText(security.maximumFailedLoginAttempts()
                + " attempts; " + security.lockDuration());
        verificationPolicy.setText(security.maximumVerificationAttempts()
                + " attempts; " + security.verificationExpiry());
        resetExpiry.setText(security.passwordResetExpiry());

        AdminApplicationSettings settings = snapshot.settings();
        approvalRequired.setSelected(
                settings.registrationRequiresAdminApproval());
        approvalRequired.setEnabled(session.isOwner() && settings.editable());
        saveSettings.setEnabled(session.isOwner() && settings.editable());

        DatabaseHealthStatus database = snapshot.database();
        databaseStatus.setText(database.status());
        databaseProduct.setText(database.databaseProduct());
        databaseMigrations.setText(Long.toString(database.appliedMigrations()));
        databaseUsers.setText(Long.toString(database.users()));
        databaseSessions.setText(Long.toString(database.activeSessions()));
    }

    private void changeStatus(AccountStatus status) {
        AuthenticatedUser selected = selected(userTable, userModel);
        if (selected == null) return;
        String verb = switch (status) {
            case ACTIVE -> "Activate";
            case SUSPENDED -> "Suspend";
            case DISABLED -> "Disable";
            default -> throw new IllegalArgumentException("Unsupported status.");
        };
        if (!ConfirmationDialogs.confirm(this, verb + " user",
                verb + " " + selected.getEmail() + "?",
                status == AccountStatus.ACTIVE
                        ? JOptionPane.QUESTION_MESSAGE
                        : JOptionPane.WARNING_MESSAGE)) return;
        String reason = promptReason(verb + " user");
        if (reason == null) return;
        runAdminTask("Account status", () -> {
            switch (status) {
                case ACTIVE -> adminService.activateUser(session,
                        selected.getUserIdentifier(), reason);
                case SUSPENDED -> adminService.suspendUser(session,
                        selected.getUserIdentifier(), reason);
                case DISABLED -> adminService.disableUser(session,
                        selected.getUserIdentifier(), reason);
                default -> throw new IllegalArgumentException("Unsupported status.");
            }
        });
    }

    private void reviewRegistration(
            boolean approve, StyledTable table, AdminUserTableModel model) {
        AuthenticatedUser selected = selected(table, model);
        if (selected == null) return;
        String action = approve ? "Approve registration" : "Reject registration";
        if (!ConfirmationDialogs.confirm(this, action,
                action + " for " + selected.getEmail() + "?",
                approve ? JOptionPane.QUESTION_MESSAGE
                        : JOptionPane.WARNING_MESSAGE)) return;
        String reason = promptReason(action);
        if (reason == null) return;
        runAdminTask(action, () -> {
            if (approve) {
                adminService.approveRegistration(session,
                        selected.getUserIdentifier(), reason);
            } else {
                adminService.rejectRegistration(session,
                        selected.getUserIdentifier(), reason);
            }
        });
    }

    private void changeAdministrator(boolean grant) {
        AuthenticatedUser selected = selected(
                administratorTable, administratorModel);
        if (selected == null) return;
        String title = grant ? "Grant ADMIN" : "Revoke ADMIN";
        if (!ConfirmationDialogs.confirm(this, title,
                title + " for " + selected.getEmail() + "?",
                JOptionPane.WARNING_MESSAGE)) return;
        String reason = promptReason(title);
        if (reason == null) return;
        char[] entered = promptPassword("Confirm OWNER password");
        if (entered == null) return;
        char[] requestPassword = Arrays.copyOf(entered, entered.length);
        Arrays.fill(entered, '\0');
        runAdminTask("Administrator access", () -> {
            try {
                if (grant) {
                    adminService.grantAdministrator(session,
                            selected.getUserIdentifier(), requestPassword, reason);
                } else {
                    adminService.revokeAdministrator(session,
                            selected.getUserIdentifier(), requestPassword, reason);
                }
            } finally {
                Arrays.fill(requestPassword, '\0');
            }
        });
    }

    private void updateSettings() {
        String reason = promptReason("Update application settings");
        if (reason == null) return;
        char[] entered = promptPassword("Confirm OWNER password");
        if (entered == null) return;
        char[] requestPassword = Arrays.copyOf(entered, entered.length);
        Arrays.fill(entered, '\0');
        boolean required = approvalRequired.isSelected();
        runAdminTask("Application settings", () -> {
            try {
                adminService.updateApplicationSettings(
                        session, required, requestPassword, reason);
            } finally {
                Arrays.fill(requestPassword, '\0');
            }
        });
    }

    private void runAdminTask(String title, Runnable action) {
        if (busy) return;
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override protected void done() {
                setBusy(false);
                try {
                    get();
                    refreshAll();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure(title, exception);
                } catch (ExecutionException exception) {
                    showFailure(title, exception.getCause());
                }
            }
        }.execute();
    }

    private void runLocalDataAction(String title, Runnable action) {
        try {
            action.run();
            refreshAll();
        } catch (RuntimeException exception) {
            ConfirmationDialogs.showError(this, title, exception);
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshButton.setEnabled(!busy);
        setCursor(Cursor.getPredefinedCursor(
                busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void showFailure(String title, Throwable failure) {
        RuntimeException exception = failure instanceof RuntimeException runtime
                ? runtime : new RuntimeException(failure.getMessage(), failure);
        ConfirmationDialogs.showError(this, title, exception);
    }

    private String promptReason(String title) {
        String reason = JOptionPane.showInputDialog(this,
                "Reason (recorded in the audit log):", title,
                JOptionPane.PLAIN_MESSAGE);
        if (reason == null) return null;
        if (reason.isBlank()) {
            JOptionPane.showMessageDialog(this, "A reason is required.", title,
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return reason.strip();
    }

    private char[] promptPassword(String title) {
        JPasswordField password = new JPasswordField(24);
        int answer = JOptionPane.showConfirmDialog(this, password, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        char[] entered = password.getPassword();
        password.setText("");
        if (answer != JOptionPane.OK_OPTION) {
            Arrays.fill(entered, '\0');
            return null;
        }
        return entered;
    }

    private void filterAdministratorCandidates() {
        String query = administratorSearch.getText().strip()
                .toLowerCase(Locale.ROOT);
        administratorModel.setRows(cachedUsers.stream()
                .filter(AuthenticatedUser::isEmailVerified)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> query.isEmpty()
                        || user.getFullName().toLowerCase(Locale.ROOT).contains(query)
                        || user.getEmail().toLowerCase(Locale.ROOT).contains(query))
                .toList());
    }

    private AuthenticatedUser selected(
            StyledTable table, AdminUserTableModel model) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.",
                    "Admin Console", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return model.getRow(table.convertRowIndexToModel(viewRow));
    }

    private static JPanel tablePanel(StyledTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 4, 8, 4));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel actionRow() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        return actions;
    }

    private static JPanel detailPanel(int rows) {
        JPanel panel = new JPanel(new GridLayout(rows, 2, 18, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 12, 24, 12));
        return panel;
    }

    private static void addDetail(JPanel panel, String name, JLabel value) {
        JLabel label = new JLabel(name);
        label.setFont(AppFonts.button());
        AppTheme.mark(label, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(label);
        panel.add(value);
    }

    private static JPanel metricCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        AppTheme.mark(card, AppTheme.CARD_ROLE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, AppColors.accent()),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JLabel label = new JLabel(title);
        label.setFont(AppFonts.caption());
        AppTheme.mark(label, AppTheme.SECONDARY_TEXT_ROLE);
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JLabel metric(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFonts.metric());
        AppTheme.mark(label, AppTheme.PRIMARY_TEXT_ROLE);
        return label;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("Checking");
        label.setFont(AppFonts.body());
        AppTheme.mark(label, AppTheme.PRIMARY_TEXT_ROLE);
        return label;
    }

    private record AdminSnapshot(
            List<AuthenticatedUser> users,
            List<AuthenticatedUser> pendingRegistrations,
            List<AuthenticatedUser> pendingVerifications,
            List<AuditEvent> auditEvents,
            AdminOverview overview,
            AdminSecurityStatus security,
            AdminApplicationSettings settings,
            DatabaseHealthStatus database) {
    }

    private static final class AdminUserTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
            "Name", "Email", "Role", "Status", "Created", "Last sign-in"
        };
        private final List<AuthenticatedUser> rows = new ArrayList<>();

        void setRows(List<AuthenticatedUser> users) {
            rows.clear();
            rows.addAll(users);
            fireTableDataChanged();
        }

        AuthenticatedUser getRow(int row) {
            return rows.get(row);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public Object getValueAt(int row, int column) {
            AuthenticatedUser user = rows.get(row);
            return switch (column) {
                case 0 -> user.getFullName();
                case 1 -> user.getEmail();
                case 2 -> user.getHighestRole().name();
                case 3 -> user.getAccountStatus().name();
                case 4 -> DateTimeFormatter.ofPattern("dd MMM uuuu")
                        .withZone(ZoneId.systemDefault())
                        .format(user.getCreatedAt());
                case 5 -> user.getLastLoginAt() == null ? "Never"
                        : DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a")
                                .withZone(ZoneId.systemDefault())
                                .format(user.getLastLoginAt());
                default -> "";
            };
        }
    }

    private static final class AuditTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
            "Time", "Action", "Actor", "Target", "Outcome", "Reason"
        };
        private final List<AuditEvent> rows = new ArrayList<>();

        void setRows(List<AuditEvent> events) {
            rows.clear();
            rows.addAll(events);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public Object getValueAt(int row, int column) {
            AuditEvent event = rows.get(row);
            return switch (column) {
                case 0 -> DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm:ss a")
                        .withZone(ZoneId.systemDefault())
                        .format(event.occurredAt());
                case 1 -> event.action().name();
                case 2 -> event.actorUserIdentifier();
                case 3 -> event.targetUserIdentifier();
                case 4 -> event.outcome();
                case 5 -> event.reason();
                default -> "";
            };
        }
    }
}
