package com.spendwise.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.ui.theme.AppFonts;
import javax.swing.JButton;

public final class SecondaryButton extends JButton {

    public SecondaryButton(String text) {
        super(text);
        setFont(AppFonts.button());
        setFocusPainted(false);
        putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
    }
}
