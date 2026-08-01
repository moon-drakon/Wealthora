package com.spendwise.ui.component;

import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class EmptyStatePanel extends JPanel {

    private final JLabel titleLabel = new JLabel();
    private final JLabel messageLabel = new JLabel();

    public EmptyStatePanel(String title, String message) {
        super(new GridBagLayout());
        AppTheme.mark(this, AppTheme.CARD_ROLE);
        titleLabel.setFont(AppFonts.sectionTitle());
        AppTheme.mark(titleLabel, AppTheme.PRIMARY_TEXT_ROLE);
        messageLabel.setFont(AppFonts.body());
        AppTheme.mark(messageLabel, AppTheme.SECONDARY_TEXT_ROLE);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(8, 16, 4, 16);
        add(titleLabel, constraints);
        constraints.gridy = 1;
        constraints.insets = new Insets(4, 16, 8, 16);
        add(messageLabel, constraints);
        setText(title, message);
    }

    public void setText(String title, String message) {
        titleLabel.setText(title);
        messageLabel.setText(message);
    }
}
