package com.spendwise.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.JTextField;

public class StyledTextField extends JTextField {

    public StyledTextField(int columns) {
        super(columns);
        putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; margin: 6,9,6,9");
    }

    public StyledTextField(String accessibleName, int columns) {
        this(columns);
        getAccessibleContext().setAccessibleName(accessibleName);
    }
}
