package com.spendwise.ui.component;

import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class SummaryCard extends JPanel {

    private final JLabel valueLabel = new JLabel("0.00");
    private final JLabel detailLabel = new JLabel(" ");

    public SummaryCard(String title, Color accentColor) {
        super(new BorderLayout(0, 8));
        AppTheme.mark(this, AppTheme.CARD_ROLE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFonts.caption());
        AppTheme.mark(titleLabel, AppTheme.SECONDARY_TEXT_ROLE);
        valueLabel.setFont(AppFonts.metric());
        AppTheme.mark(valueLabel, AppTheme.PRIMARY_TEXT_ROLE);
        detailLabel.setFont(AppFonts.caption());
        detailLabel.setForeground(AppColors.secondaryText());
        AppTheme.mark(detailLabel, AppTheme.SECONDARY_TEXT_ROLE);

        add(titleLabel, BorderLayout.NORTH);
        add(valueLabel, BorderLayout.CENTER);
        add(detailLabel, BorderLayout.SOUTH);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public String getValue() {
        return valueLabel.getText();
    }

    public void setDetail(String detail) {
        detailLabel.setText(detail == null || detail.isBlank() ? " " : detail);
    }
}
