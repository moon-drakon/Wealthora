package com.spendwise.ui.shell;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.config.AppBrand;
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
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JToggleButton;

public final class Sidebar extends JPanel {

    private static final Color SIDEBAR_TEXT = new Color(226, 235, 237);
    private static final Color SIDEBAR_MUTED = new Color(165, 188, 192);
    private static final Color SELECTED_BACKGROUND = new Color(45, 112, 98);

    private final ButtonGroup navigationGroup = new ButtonGroup();
    private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();
    private final Consumer<String> selectionListener;
    private final JPanel navigationPanel = new JPanel();

    public Sidebar(Consumer<String> selectionListener) {
        this.selectionListener = java.util.Objects.requireNonNull(
                selectionListener, "Sidebar selection listener is required.");
        setLayout(new java.awt.BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 12, 12, 10));
        setPreferredSize(new Dimension(224, 600));
        AppTheme.mark(this, AppTheme.SIDEBAR_ROLE);

        JPanel brandingPanel = new JPanel();
        brandingPanel.setLayout(new BoxLayout(brandingPanel, BoxLayout.Y_AXIS));
        brandingPanel.setOpaque(false);
        addBranding(brandingPanel);
        brandingPanel.add(Box.createVerticalStrut(20));
        add(brandingPanel, java.awt.BorderLayout.NORTH);

        navigationPanel.setLayout(new BoxLayout(
                navigationPanel, BoxLayout.Y_AXIS));
        navigationPanel.setOpaque(false);
        JScrollPane navigationScroll = new JScrollPane(navigationPanel);
        navigationScroll.setBorder(BorderFactory.createEmptyBorder());
        navigationScroll.setOpaque(false);
        navigationScroll.getViewport().setOpaque(false);
        navigationScroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        navigationScroll.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        navigationScroll.getVerticalScrollBar().setUnitIncrement(14);
        add(navigationScroll, java.awt.BorderLayout.CENTER);
    }

    public void addSection(String label) {
        if (navigationPanel.getComponentCount() > 0) {
            navigationPanel.add(Box.createVerticalStrut(12));
        }
        JLabel heading = new JLabel(label.toUpperCase(java.util.Locale.ROOT));
        heading.setFont(AppFonts.caption().deriveFont(
                java.awt.Font.BOLD, 10f));
        heading.setForeground(SIDEBAR_MUTED);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 12, 5, 0));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigationPanel.add(heading);
    }

    public void addNavigationItem(
            String identifier, String label, Icon icon) {
        JToggleButton button = new JToggleButton(label, icon);
        button.setHorizontalAlignment(JToggleButton.LEFT);
        button.setIconTextGap(12);
        button.setFont(AppFonts.button());
        button.setForeground(SIDEBAR_TEXT);
        button.setFocusPainted(true);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.putClientProperty(
                FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.addItemListener(event -> {
            button.setOpaque(button.isSelected());
            button.setContentAreaFilled(button.isSelected());
            button.setBackground(button.isSelected()
                    ? SELECTED_BACKGROUND : AppColors.sidebarBackground());
        });
        button.addActionListener(event -> selectionListener.accept(identifier));
        navigationGroup.add(button);
        buttons.put(identifier, button);
        navigationPanel.add(button);
        navigationPanel.add(Box.createVerticalStrut(2));
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

    private static void addBranding(JPanel panel) {
        JLabel brand = new JLabel(AppBrand.APP_NAME);
        brand.setFont(AppFonts.pageTitle());
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(AppBrand.TAGLINE.toUpperCase(
                java.util.Locale.ROOT));
        subtitle.setFont(AppFonts.caption().deriveFont(11f));
        subtitle.setForeground(SIDEBAR_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(brand);
        panel.add(Box.createVerticalStrut(2));
        panel.add(subtitle);
    }
}
