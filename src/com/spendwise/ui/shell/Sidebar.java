package com.spendwise.ui.shell;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

public final class Sidebar extends JPanel {

    private static final Color SIDEBAR_TEXT = new Color(226, 235, 237);
    private static final Color SIDEBAR_MUTED = new Color(165, 188, 192);
    private static final Color SELECTED_BACKGROUND = new Color(45, 112, 98);

    private final ButtonGroup navigationGroup = new ButtonGroup();
    private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();
    private final Consumer<String> selectionListener;

    public Sidebar(Consumer<String> selectionListener) {
        this.selectionListener = java.util.Objects.requireNonNull(
                selectionListener, "Sidebar selection listener is required.");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(20, 14, 16, 14));
        setPreferredSize(new Dimension(218, 600));
        AppTheme.mark(this, AppTheme.SIDEBAR_ROLE);
        addBranding();
        add(Box.createVerticalStrut(24));
    }

    public void addNavigationItem(
            String identifier, String label, Icon icon) {
        JToggleButton button = new JToggleButton(label, icon);
        button.setHorizontalAlignment(JToggleButton.LEFT);
        button.setIconTextGap(12);
        button.setFont(AppFonts.button());
        button.setForeground(SIDEBAR_TEXT);
        button.setFocusPainted(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.putClientProperty(
                FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.addItemListener(event -> {
            button.setOpaque(button.isSelected());
            button.setBackground(button.isSelected()
                    ? SELECTED_BACKGROUND : AppColors.sidebarBackground());
        });
        button.addActionListener(event -> selectionListener.accept(identifier));
        navigationGroup.add(button);
        buttons.put(identifier, button);
        add(button);
        add(Box.createVerticalStrut(3));
    }

    public void select(String identifier) {
        JToggleButton button = buttons.get(identifier);
        if (button != null) {
            button.setSelected(true);
        }
    }

    public void refreshTheme() {
        setBackground(AppColors.sidebarBackground());
        for (JToggleButton button : buttons.values()) {
            button.setForeground(SIDEBAR_TEXT);
            button.setBackground(button.isSelected()
                    ? SELECTED_BACKGROUND : AppColors.sidebarBackground());
        }
    }

    private void addBranding() {
        JLabel brand = new JLabel("SpendWise");
        brand.setFont(AppFonts.pageTitle());
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("PERSONAL FINANCE");
        subtitle.setFont(AppFonts.caption().deriveFont(11f));
        subtitle.setForeground(SIDEBAR_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(brand);
        add(Box.createVerticalStrut(2));
        add(subtitle);
    }
}
