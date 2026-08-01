package com.spendwise.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import javax.swing.JButton;

public final class PrimaryButton extends JButton {

    public PrimaryButton(String text) {
        super(text);
        setFont(AppFonts.button());
        setBackground(AppColors.accent());
        setForeground(java.awt.Color.WHITE);
        setFocusPainted(false);
        putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; borderWidth: 0; focusWidth: 1; innerFocusWidth: 0");
    }
}
