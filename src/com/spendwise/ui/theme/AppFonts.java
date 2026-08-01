package com.spendwise.ui.theme;

import java.awt.Font;
import javax.swing.UIManager;

public final class AppFonts {

    private AppFonts() {
    }

    public static Font body() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : font;
    }

    public static Font caption() {
        return body().deriveFont(Font.PLAIN, 12f);
    }

    public static Font button() {
        return body().deriveFont(Font.BOLD, 13f);
    }

    public static Font pageTitle() {
        return body().deriveFont(Font.BOLD, 24f);
    }

    public static Font sectionTitle() {
        return body().deriveFont(Font.BOLD, 17f);
    }

    public static Font metric() {
        return body().deriveFont(Font.BOLD, 23f);
    }
}
