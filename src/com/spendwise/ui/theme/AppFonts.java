package com.spendwise.ui.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.UIManager;

public final class AppFonts {

    private static final String BANGLA_SAMPLE =
            "বাংলা ০১২৩৪৫৬৭৮৯";
    private static final String MULTILINGUAL_FAMILY =
            findMultilingualFamily();

    private AppFonts() {
    }

    public static Font body() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : font;
    }

    public static Font caption() {
        return body().deriveFont(Font.PLAIN, 12f);
    }

    public static Font multilingualBody() {
        return multilingual(body());
    }

    public static Font multilingualCaption() {
        return multilingual(caption());
    }

    public static Font multilingualButton() {
        return multilingual(button());
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

    private static Font multilingual(Font base) {
        if (MULTILINGUAL_FAMILY == null) return base;
        return new Font(MULTILINGUAL_FAMILY, base.getStyle(), base.getSize())
                .deriveFont(base.getSize2D());
    }

    private static String findMultilingualFamily() {
        String[] installed = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Set<String> candidates = new LinkedHashSet<>(java.util.List.of(
                "Noto Sans Bengali", "Nirmala UI", "Nirmala Text",
                "Kalpurush", "Siyam Rupali"));
        candidates.addAll(java.util.List.of(installed));
        for (String candidate : candidates) {
            Font font = new Font(candidate, Font.PLAIN, 13);
            if (!Font.DIALOG.equals(font.getFamily())
                    && font.canDisplayUpTo(BANGLA_SAMPLE) == -1) {
                return candidate;
            }
        }
        return null;
    }
}
