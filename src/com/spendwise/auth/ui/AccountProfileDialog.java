package com.spendwise.auth.ui;

import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class AccountProfileDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a")
                    .withZone(ZoneId.systemDefault());

    public AccountProfileDialog(Window owner, UserSession session) {
        super(owner, "My Profile", Dialog.ModalityType.APPLICATION_MODAL);
        UserSession required = Objects.requireNonNull(session);
        JPanel content = new JPanel(new BorderLayout(0, 18));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JLabel title = new JLabel(required.getDisplayName());
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        content.add(title, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridBagLayout());
        AppTheme.mark(details, AppTheme.CARD_ROLE);
        details.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        int row = 0;
        addDetail(details, row++, "Email", required.getEmail());
        addDetail(details, row++, "Roles",
                required.getUser().getRoles().stream().sorted()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(", ")));
        addDetail(details, row++, "Status",
                required.getUser().getAccountStatus().name());
        addDetail(details, row++, "Authentication",
                required.getProvider().name().replace('_', ' '));
        addDetail(details, row++, "Account created",
                DATE_TIME.format(required.getUser().getCreatedAt()));
        addDetail(details, row++, "Signed in",
                DATE_TIME.format(required.getAuthenticatedAt()));
        addDetail(details, row++, "Last login",
                required.getUser().getLastLoginAt() == null ? "Never"
                        : DATE_TIME.format(
                                required.getUser().getLastLoginAt()));
        addDetail(details, row++, "Preferred theme",
                required.getUser().getPreferredTheme());
        addDetail(details, row++, "Preferred currency",
                required.getUser().getPreferredCurrency());
        addDetail(details, row, "Verification",
                String.join(" · ", required.getUser().getProfileBadges()));
        content.add(details, BorderLayout.CENTER);

        JPanel footer = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        SecondaryButton close = new SecondaryButton("Close");
        close.addActionListener(event -> dispose());
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(580, 560);
        setMinimumSize(new java.awt.Dimension(500, 480));
        setLocationRelativeTo(owner);
    }

    private static void addDetail(
            JPanel panel, int row, String labelText, String valueText) {
        GridBagConstraints labelConstraints = constraints(row, 0);
        labelConstraints.insets = new Insets(7, 0, 7, 18);
        JLabel label = new JLabel(labelText);
        label.setFont(AppFonts.button());
        AppTheme.mark(label, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(label, labelConstraints);

        GridBagConstraints valueConstraints = constraints(row, 1);
        valueConstraints.weightx = 1;
        valueConstraints.insets = new Insets(7, 0, 7, 0);
        JLabel value = new JLabel(valueText == null || valueText.isBlank()
                ? "Not set" : valueText);
        value.setFont(AppFonts.body());
        AppTheme.mark(value, AppTheme.PRIMARY_TEXT_ROLE);
        panel.add(value, valueConstraints);
    }

    private static GridBagConstraints constraints(int row, int column) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        return constraints;
    }
}
