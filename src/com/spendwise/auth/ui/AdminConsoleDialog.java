package com.spendwise.auth.ui;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.UserRole;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.admin.AdminOverview;
import com.spendwise.auth.admin.AdminService;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.ui.component.ConfirmationDialogs;
import com.spendwise.ui.component.EmptyStatePanel;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class AdminConsoleDialog extends JDialog {

    private final AdminService adminService;
    private final UserSession session;
    private final AdminUserTableModel userModel = new AdminUserTableModel();
    private final AdminUserTableModel administratorModel =
            new AdminUserTableModel();
    private final AuditTableModel auditModel = new AuditTableModel();
    private final StyledTable userTable = new StyledTable(userModel);
    private final StyledTable administratorTable =
            new StyledTable(administratorModel);
    private final JLabel totalUsers = metric("0");
    private final JLabel activeUsers = metric("0");
    private final JLabel suspendedUsers = metric("0");
    private final JLabel administrators = metric("0");
    private final JLabel owners = metric("0");
    private final JLabel standardUsers = metric("0");
    private final JLabel failedAttempts = metric("0");
    private final JLabel lastBackup = metric("None");
    private final JLabel storageStatus = metric("Checking");
    private final List<AuthenticatedUser> cachedUsers = new ArrayList<>();
    private final StyledTextField administratorSearch =
            new StyledTextField("Search active verified users", 28);

    public AdminConsoleDialog(
            Window owner, AdminService adminService, UserSession session) {
        super(owner, "Admin Console", Dialog.ModalityType.APPLICATION_MODAL);
        this.adminService = Objects.requireNonNull(adminService);
        this.session = Objects.requireNonNull(session);
        // Service-layer authorization is authoritative; this also fails closed.
        adminService.getOverview(session);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Admin Console");
        title.setFont(AppFonts.pageTitle());
        JLabel identity = new JLabel(session.getDisplayName() + " · "
                + session.getUser().getHighestRole().name());
        identity.setFont(AppFonts.caption());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        AppTheme.mark(identity, AppTheme.SECONDARY_TEXT_ROLE);
        heading.add(title, BorderLayout.WEST);
        heading.add(identity, BorderLayout.EAST);
        content.add(heading, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", overviewPanel());
        tabs.addTab("Users", usersPanel());
        if (session.isOwner()) {
            tabs.addTab("Administrators", administratorsPanel());
        }
        tabs.addTab("Audit Logs", auditPanel());
        tabs.addTab("Security", unavailable(
                "Security configuration",
                "Authentication policy is enforced locally; remote security settings require a backend."));
        tabs.addTab("Application Settings", unavailable(
                "Application settings",
                "Use the main Settings page for desktop preferences."));
        tabs.addTab("Backup and Restore", unavailable(
                "Backup and restore",
                "Use Data in My Finance for versioned backup and validated restore."));
        tabs.addTab("Database Health", unavailable(
                "Local data health",
                "User and audit repositories are validated whenever they are read."));
        content.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        SecondaryButton refresh = new SecondaryButton("Refresh");
        refresh.addActionListener(event -> refreshAll());
        PrimaryButton back = new PrimaryButton("Back to My Finance");
        back.addActionListener(event -> dispose());
        footer.add(refresh);
        footer.add(back);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(1080, 720);
        setMinimumSize(new java.awt.Dimension(900, 600));
        setLocationRelativeTo(owner);
        refreshAll();
    }

    private JPanel overviewPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 3, 14, 14));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 4, 18, 4));
        panel.setOpaque(false);
        panel.add(metricCard("Total users", totalUsers));
        panel.add(metricCard("Active users", activeUsers));
        panel.add(metricCard("Suspended users", suspendedUsers));
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
        activate.addActionListener(event -> changeStatus(true));
        SecondaryButton suspend = new SecondaryButton("Suspend");
        suspend.addActionListener(event -> changeStatus(false));
        actions.add(activate);
        actions.add(suspend);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel administratorsPanel() {
        JPanel panel = tablePanel(administratorTable);
        JPanel searchArea = new JPanel(new BorderLayout(8, 0));
        searchArea.setOpaque(false);
        JLabel searchLabel = new JLabel("Find active verified user");
        searchLabel.setFont(AppFonts.button());
        searchArea.add(searchLabel, BorderLayout.WEST);
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

    private JPanel auditPanel() {
        return tablePanel(new StyledTable(auditModel));
    }

    private void refreshAll() {
        try {
            List<AuthenticatedUser> users = adminService.listUsers(session);
            cachedUsers.clear();
            cachedUsers.addAll(users);
            userModel.setRows(users);
            filterAdministratorCandidates();
            auditModel.setRows(adminService.getAuditEvents(session));
            AdminOverview overview = adminService.getOverview(session);
            totalUsers.setText(Integer.toString(overview.totalUsers()));
            activeUsers.setText(Integer.toString(overview.activeUsers()));
            suspendedUsers.setText(Integer.toString(overview.suspendedUsers()));
            owners.setText(Integer.toString(overview.owners()));
            administrators.setText(Integer.toString(overview.administrators()));
            standardUsers.setText(Integer.toString(overview.standardUsers()));
            failedAttempts.setText(Integer.toString(
                    overview.failedLoginAttempts()));
            lastBackup.setText(overview.lastBackup());
            lastBackup.setToolTipText(overview.lastBackup());
            storageStatus.setText(overview.storageStatus());
        } catch (RuntimeException exception) {
            ConfirmationDialogs.showError(
                    this, "Admin Console", exception);
        }
    }

    private void changeStatus(boolean activate) {
        AuthenticatedUser selected = selected(userTable, userModel);
        if (selected == null) return;
        String reason = promptReason(activate ? "Activate user" : "Suspend user");
        if (reason == null) return;
        try {
            if (activate) {
                adminService.activateUser(session,
                        selected.getUserIdentifier(), reason);
            } else if (ConfirmationDialogs.confirmDestructive(this,
                    "Suspend user", "Suspend " + selected.getEmail()
                            + "? They will not be able to sign in.")) {
                adminService.suspendUser(session,
                        selected.getUserIdentifier(), reason);
            } else {
                return;
            }
            refreshAll();
        } catch (RuntimeException exception) {
            ConfirmationDialogs.showError(this, "Account status", exception);
        }
    }

    private void changeAdministrator(boolean grant) {
        AuthenticatedUser selected = selected(
                administratorTable, administratorModel);
        if (selected == null) return;
        String reason = promptReason(
                grant ? "Grant administrator" : "Revoke administrator");
        if (reason == null) return;
        if (!ConfirmationDialogs.confirm(this,
                grant ? "Grant ADMIN" : "Revoke ADMIN",
                (grant ? "Grant" : "Revoke") + " administrator access for "
                        + selected.getEmail() + "?",
                JOptionPane.WARNING_MESSAGE)) {
            return;
        }
        JPasswordField password = new JPasswordField(24);
        int answer = JOptionPane.showConfirmDialog(this, password,
                "Confirm OWNER password", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        char[] entered = password.getPassword();
        try {
            if (grant) {
                adminService.grantAdministrator(session,
                        selected.getUserIdentifier(), entered, reason);
            } else {
                adminService.revokeAdministrator(session,
                        selected.getUserIdentifier(), entered, reason);
            }
            refreshAll();
        } catch (RuntimeException exception) {
            ConfirmationDialogs.showError(
                    this, "Administrator access", exception);
        } finally {
            Arrays.fill(entered, '\0');
            password.setText("");
        }
    }

    private String promptReason(String title) {
        String reason = JOptionPane.showInputDialog(
                this, "Reason (recorded in the audit log):", title,
                JOptionPane.PLAIN_MESSAGE);
        return reason == null ? null : reason.strip();
    }

    private void filterAdministratorCandidates() {
        String query = administratorSearch.getText().strip()
                .toLowerCase(java.util.Locale.ROOT);
        administratorModel.setRows(cachedUsers.stream()
                .filter(AuthenticatedUser::isEmailVerified)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> query.isEmpty()
                        || user.getFullName().toLowerCase(
                                java.util.Locale.ROOT).contains(query)
                        || user.getEmail().toLowerCase(
                                java.util.Locale.ROOT).contains(query))
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

    private static JPanel metricCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        AppTheme.mark(card, AppTheme.CARD_ROLE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0,
                        AppColors.accent()),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
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

    private static JPanel unavailable(String title, String detail) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(new EmptyStatePanel(title, detail), BorderLayout.CENTER);
        return panel;
    }

    private static final class AdminUserTableModel
            extends AbstractTableModel {

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
        @Override public String getColumnName(int column) {
            return COLUMNS[column];
        }
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
        @Override public String getColumnName(int column) {
            return COLUMNS[column];
        }
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
