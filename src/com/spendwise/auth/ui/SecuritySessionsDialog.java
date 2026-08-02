package com.spendwise.auth.ui;

import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class SecuritySessionsDialog extends JDialog {

    public SecuritySessionsDialog(Window owner, UserSession session) {
        super(owner, "Security and Sessions",
                Dialog.ModalityType.APPLICATION_MODAL);
        UserSession required = Objects.requireNonNull(session);
        JPanel content = new JPanel(new BorderLayout(0, 18));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JLabel title = new JLabel("Security and Sessions");
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        content.add(title, BorderLayout.NORTH);

        JPanel card = new JPanel(new BorderLayout(0, 10));
        AppTheme.mark(card, AppTheme.CARD_ROLE);
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JLabel device = new JLabel("This Windows application");
        device.setFont(AppFonts.sectionTitle());
        AppTheme.mark(device, AppTheme.PRIMARY_TEXT_ROLE);
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("dd MMM uuuu, hh:mm a")
                .withZone(ZoneId.systemDefault());
        JLabel detail = new JLabel("Signed in "
                + formatter.format(required.getAuthenticatedAt()) + " · "
                + required.getProvider().name().replace('_', ' '));
        detail.setFont(AppFonts.body());
        AppTheme.mark(detail, AppTheme.SECONDARY_TEXT_ROLE);
        JLabel notice = new JLabel(
                "Only the current in-memory session is active. Sign Out clears it immediately.");
        notice.setFont(AppFonts.caption());
        AppTheme.mark(notice, AppTheme.SECONDARY_TEXT_ROLE);
        card.add(device, BorderLayout.NORTH);
        card.add(detail, BorderLayout.CENTER);
        card.add(notice, BorderLayout.SOUTH);
        content.add(card, BorderLayout.CENTER);

        JPanel footer = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        SecondaryButton close = new SecondaryButton("Close");
        close.addActionListener(event -> dispose());
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(620, 330);
        setLocationRelativeTo(owner);
    }
}
