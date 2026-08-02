package com.spendwise.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;

public final class StyledComboBox<E> extends JComboBox<E> {

    public StyledComboBox() {
        style();
    }

    public StyledComboBox(E[] items) {
        super(items);
        style();
    }

    public StyledComboBox(ComboBoxModel<E> model) {
        super(model);
        style();
    }

    private void style() {
        putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; padding: 5,8,5,8");
    }
}
