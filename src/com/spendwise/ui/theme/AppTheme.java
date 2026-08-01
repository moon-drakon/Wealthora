package com.spendwise.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class AppTheme {

    public static final String COLOR_ROLE = "spendwise.colorRole";
    public static final String PAGE_ROLE = "page";
    public static final String CARD_ROLE = "card";
    public static final String SIDEBAR_ROLE = "sidebar";
    public static final String PRIMARY_TEXT_ROLE = "primaryText";
    public static final String SECONDARY_TEXT_ROLE = "secondaryText";

    private static boolean darkMode;

    private AppTheme() {
    }

    public static void initialize() {
        darkMode = false;
        installLookAndFeel();
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void toggle() {
        setDarkMode(!darkMode);
    }

    public static void setDarkMode(boolean useDarkMode) {
        if (darkMode == useDarkMode && UIManager.getLookAndFeel() != null) {
            return;
        }
        darkMode = useDarkMode;
        installLookAndFeel();
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            applyCustomColors(window);
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    public static void applyCustomColors(Component component) {
        applyRole(component);
        if (component instanceof JTable table) {
            table.setGridColor(AppColors.border());
            table.setSelectionBackground(AppColors.selectionBackground());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyCustomColors(child);
            }
        }
    }

    public static void mark(JComponent component, String role) {
        component.putClientProperty(COLOR_ROLE, role);
        applyRole(component);
    }

    private static void installLookAndFeel() {
        try {
            if (darkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (RuntimeException exception) {
            applySystemFallback();
        }
        configureDefaults();
    }

    private static void configureDefaults() {
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.selectionBackground", AppColors.selectionBackground());
        UIManager.put("Table.gridColor", AppColors.border());
        UIManager.put("TabbedPane.showTabSeparators", true);
    }

    private static void applySystemFallback() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException
                | UnsupportedLookAndFeelException
                | SecurityException ignored) {
            // Swing's cross-platform look and feel remains available.
        }
    }

    private static void applyRole(Component component) {
        if (!(component instanceof JComponent swingComponent)) {
            return;
        }
        Object role = swingComponent.getClientProperty(COLOR_ROLE);
        if (role == null) {
            role = detectLegacyRole(component);
            if (role != null) {
                swingComponent.putClientProperty(COLOR_ROLE, role);
            }
        }
        if (PAGE_ROLE.equals(role)) {
            component.setBackground(AppColors.pageBackground());
        } else if (CARD_ROLE.equals(role)) {
            component.setBackground(AppColors.cardBackground());
        } else if (SIDEBAR_ROLE.equals(role)) {
            component.setBackground(AppColors.sidebarBackground());
        } else if (PRIMARY_TEXT_ROLE.equals(role)) {
            component.setForeground(AppColors.primaryText());
        } else if (SECONDARY_TEXT_ROLE.equals(role)) {
            component.setForeground(AppColors.secondaryText());
        }
    }

    private static String detectLegacyRole(Component component) {
        Color background = component.getBackground();
        if (matches(background, 244, 247, 250)) {
            return PAGE_ROLE;
        }
        if (background != null && background.equals(Color.WHITE)
                && component instanceof javax.swing.JPanel) {
            return CARD_ROLE;
        }
        Color foreground = component.getForeground();
        if (matches(foreground, 47, 58, 68)
                || matches(foreground, 42, 92, 130)) {
            return PRIMARY_TEXT_ROLE;
        }
        if (matches(foreground, 80, 90, 100)
                || matches(foreground, 92, 104, 115)) {
            return SECONDARY_TEXT_ROLE;
        }
        return null;
    }

    private static boolean matches(Color color, int red, int green, int blue) {
        return color != null && color.getRed() == red
                && color.getGreen() == green && color.getBlue() == blue;
    }
}
