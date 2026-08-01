package com.spendwise.ui.theme;

import java.awt.Color;
import javax.swing.UIManager;

public final class AppColors {

    private static final Color LIGHT_PAGE = new Color(244, 247, 250);
    private static final Color DARK_PAGE = new Color(24, 27, 32);
    private static final Color LIGHT_CARD = Color.WHITE;
    private static final Color DARK_CARD = new Color(35, 39, 46);
    private static final Color LIGHT_SIDEBAR = new Color(25, 53, 65);
    private static final Color DARK_SIDEBAR = new Color(20, 25, 31);
    private static final Color ACCENT = new Color(31, 126, 96);
    private static final Color ACCENT_HOVER = new Color(25, 105, 80);
    private static final Color INCOME = new Color(28, 132, 93);
    private static final Color EXPENSE = new Color(196, 63, 68);
    private static final Color TRANSFER = new Color(53, 112, 180);
    private static final Color WARNING = new Color(196, 132, 37);

    private AppColors() {
    }

    public static Color pageBackground() {
        return AppTheme.isDarkMode() ? DARK_PAGE : LIGHT_PAGE;
    }

    public static Color cardBackground() {
        return AppTheme.isDarkMode() ? DARK_CARD : LIGHT_CARD;
    }

    public static Color sidebarBackground() {
        return AppTheme.isDarkMode() ? DARK_SIDEBAR : LIGHT_SIDEBAR;
    }

    public static Color primaryText() {
        return color("Label.foreground", AppTheme.isDarkMode()
                ? new Color(229, 233, 238) : new Color(41, 50, 58));
    }

    public static Color secondaryText() {
        return AppTheme.isDarkMode()
                ? new Color(165, 174, 184) : new Color(91, 103, 114);
    }

    public static Color border() {
        return AppTheme.isDarkMode()
                ? new Color(63, 69, 78) : new Color(218, 225, 231);
    }

    public static Color accent() {
        return ACCENT;
    }

    public static Color accentHover() {
        return ACCENT_HOVER;
    }

    public static Color income() {
        return INCOME;
    }

    public static Color expense() {
        return EXPENSE;
    }

    public static Color transfer() {
        return TRANSFER;
    }

    public static Color warning() {
        return WARNING;
    }

    public static Color selectionBackground() {
        return AppTheme.isDarkMode()
                ? new Color(44, 82, 74) : new Color(218, 239, 232);
    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }
}
