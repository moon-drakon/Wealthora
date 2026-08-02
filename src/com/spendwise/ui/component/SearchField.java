package com.spendwise.ui.component;

import com.formdev.flatlaf.FlatClientProperties;

public final class SearchField extends StyledTextField {

    public SearchField(String placeholder, int columns) {
        super(columns);
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; margin: 6,10,6,10");
        getAccessibleContext().setAccessibleName(placeholder);
    }
}
