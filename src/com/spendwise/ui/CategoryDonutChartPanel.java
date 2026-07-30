package com.spendwise.ui;

import com.spendwise.model.Category;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.JPanel;

final class CategoryDonutChartPanel extends JPanel {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final Color TEXT_COLOR = new Color(47, 58, 68);
    private static final Color SECONDARY_TEXT = new Color(92, 104, 115);
    private static final Color DONUT_HOLE = Color.WHITE;
    private static final Color[] CATEGORY_COLORS = {
        new Color(42, 121, 155),
        new Color(69, 143, 136),
        new Color(104, 116, 171),
        new Color(215, 142, 65),
        new Color(192, 85, 93),
        new Color(118, 151, 78),
        new Color(151, 100, 163),
        new Color(126, 137, 147)
    };

    private Map<Category, BigDecimal> categoryTotals = emptyCategoryTotals();

    CategoryDonutChartPanel() {
        this(Map.of());
    }

    CategoryDonutChartPanel(Map<Category, BigDecimal> categoryTotals) {
        setBackground(Color.WHITE);
        setOpaque(true);
        setPreferredSize(new Dimension(540, 320));
        getAccessibleContext().setAccessibleName("Selected-month category chart");
        getAccessibleContext().setAccessibleDescription(
                "Donut chart and legend of selected-month expense totals by category");
        replaceData(categoryTotals);
    }

    void replaceData(Map<Category, BigDecimal> newCategoryTotals) {
        categoryTotals = copyCategoryTotals(newCategoryTotals);
        repaint();
    }

    Map<Category, BigDecimal> getDataSnapshot() {
        return categoryTotals;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D chartGraphics = (Graphics2D) graphics.create();
        try {
            chartGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            chartGraphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            chartGraphics.setColor(TEXT_COLOR);
            chartGraphics.setFont(getFont().deriveFont(Font.BOLD, 16f));
            chartGraphics.drawString("Selected-Month Categories", 22, 27);

            BigDecimal totalAmount = positiveTotal();
            drawDonut(chartGraphics, totalAmount);
            drawLegend(chartGraphics);
        } finally {
            chartGraphics.dispose();
        }
    }

    private void drawDonut(Graphics2D graphics, BigDecimal totalAmount) {
        int availableWidth = Math.max(160, (int) (getWidth() * 0.48));
        int availableHeight = Math.max(140, getHeight() - 76);
        int diameter = Math.max(90, Math.min(210, Math.min(
                availableWidth - 24, availableHeight - 18)));
        int centerX = 20 + availableWidth / 2;
        int centerY = 50 + availableHeight / 2;
        int donutX = centerX - diameter / 2;
        int donutY = centerY - diameter / 2;

        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            graphics.setColor(new Color(230, 234, 238));
            graphics.fillOval(donutX, donutY, diameter, diameter);
            int holeDiameter = (int) (diameter * 0.58);
            graphics.setColor(DONUT_HOLE);
            graphics.fillOval(
                    centerX - holeDiameter / 2,
                    centerY - holeDiameter / 2,
                    holeDiameter,
                    holeDiameter);
            drawCenteredText(graphics, "No category spending", centerX, centerY - 3, 12f);
            drawCenteredText(graphics, "0.00", centerX, centerY + 17, 15f);
            return;
        }

        int startAngle = 90;
        int remainingAngle = 360;
        int remainingPositiveCategories = positiveCategoryCount();
        for (Category category : Category.values()) {
            BigDecimal amount = categoryTotals.get(category);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            remainingPositiveCategories--;
            int arcAngle;
            if (remainingPositiveCategories == 0) {
                arcAngle = remainingAngle;
            } else {
                double angle = amount.doubleValue() / totalAmount.doubleValue() * 360.0;
                int maximumAngle =
                        Math.max(1, remainingAngle - remainingPositiveCategories);
                arcAngle = Math.max(
                        1, Math.min(maximumAngle, (int) Math.round(angle)));
            }
            graphics.setColor(CATEGORY_COLORS[category.ordinal()]);
            graphics.fillArc(donutX, donutY, diameter, diameter, startAngle, -arcAngle);
            startAngle -= arcAngle;
            remainingAngle -= arcAngle;
        }

        int holeDiameter = (int) (diameter * 0.58);
        graphics.setColor(DONUT_HOLE);
        graphics.fillOval(
                centerX - holeDiameter / 2,
                centerY - holeDiameter / 2,
                holeDiameter,
                holeDiameter);
        drawCenteredText(graphics, "Month total", centerX, centerY - 4, 11f);
        drawCenteredText(graphics, totalAmount.toPlainString(), centerX, centerY + 18, 16f);
    }

    private void drawLegend(Graphics2D graphics) {
        int legendX = Math.max(270, (int) (getWidth() * 0.53));
        int legendY = 58;
        int rowHeight = Math.max(25, (getHeight() - legendY - 14) / Category.values().length);
        graphics.setFont(getFont().deriveFont(12f));

        for (Category category : Category.values()) {
            int rowY = legendY + category.ordinal() * rowHeight;
            graphics.setColor(CATEGORY_COLORS[category.ordinal()]);
            graphics.fillRoundRect(legendX, rowY, 12, 12, 3, 3);

            graphics.setColor(TEXT_COLOR);
            graphics.drawString(category.getDisplayName(), legendX + 20, rowY + 11);

            String amountText = categoryTotals.get(category).toPlainString();
            FontMetrics metrics = graphics.getFontMetrics();
            int amountX = Math.max(
                    legendX + 135,
                    getWidth() - 22 - metrics.stringWidth(amountText));
            graphics.setColor(SECONDARY_TEXT);
            graphics.drawString(amountText, amountX, rowY + 11);
        }
    }

    private void drawCenteredText(
            Graphics2D graphics, String text, int centerX, int baselineY, float fontSize) {
        graphics.setFont(getFont().deriveFont(
                fontSize >= 15f ? Font.BOLD : Font.PLAIN, fontSize));
        graphics.setColor(fontSize >= 15f ? TEXT_COLOR : SECONDARY_TEXT);
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - textWidth / 2, baselineY);
    }

    private BigDecimal positiveTotal() {
        BigDecimal total = ZERO_AMOUNT;
        for (BigDecimal amount : categoryTotals.values()) {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(amount);
            }
        }
        return total;
    }

    private int positiveCategoryCount() {
        int count = 0;
        for (BigDecimal amount : categoryTotals.values()) {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                count++;
            }
        }
        return count;
    }

    private static Map<Category, BigDecimal> copyCategoryTotals(
            Map<Category, BigDecimal> suppliedTotals) {
        Objects.requireNonNull(suppliedTotals, "Category chart data is required.");
        for (Map.Entry<Category, BigDecimal> entry : suppliedTotals.entrySet()) {
            Objects.requireNonNull(
                    entry.getKey(), "Category chart data cannot contain null category keys.");
            Objects.requireNonNull(
                    entry.getValue(), "Category chart data cannot contain null values.");
        }

        EnumMap<Category, BigDecimal> copiedTotals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            BigDecimal amount = suppliedTotals.getOrDefault(category, ZERO_AMOUNT);
            copiedTotals.put(category, amount.setScale(2, RoundingMode.UNNECESSARY));
        }
        return Collections.unmodifiableMap(copiedTotals);
    }

    private static Map<Category, BigDecimal> emptyCategoryTotals() {
        EnumMap<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            totals.put(category, ZERO_AMOUNT);
        }
        return Collections.unmodifiableMap(totals);
    }
}
