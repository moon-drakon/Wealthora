package com.spendwise.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.JPanel;

final class MonthlyBarChartPanel extends JPanel {

    private static final Color TEXT_COLOR = new Color(47, 58, 68);
    private static final Color SECONDARY_TEXT = new Color(92, 104, 115);
    private static final Color BAR_COLOR = new Color(42, 121, 155);
    private static final Color BAR_HIGHLIGHT = new Color(30, 92, 130);
    private static final Color BASELINE_COLOR = new Color(190, 199, 207);
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    private Map<YearMonth, BigDecimal> monthlyTotals = Map.of();

    MonthlyBarChartPanel() {
        this(Map.of());
    }

    MonthlyBarChartPanel(Map<YearMonth, BigDecimal> monthlyTotals) {
        setBackground(Color.WHITE);
        setOpaque(true);
        setPreferredSize(new Dimension(540, 300));
        getAccessibleContext().setAccessibleName("Recent monthly spending chart");
        getAccessibleContext().setAccessibleDescription(
                "Bar chart of monthly expense totals ending in the selected month");
        replaceData(monthlyTotals);
    }

    void replaceData(Map<YearMonth, BigDecimal> newMonthlyTotals) {
        monthlyTotals = copyMonthlyTotals(newMonthlyTotals);
        repaint();
    }

    Map<YearMonth, BigDecimal> getDataSnapshot() {
        return monthlyTotals;
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

            drawTitle(chartGraphics);
            drawBars(chartGraphics);
        } finally {
            chartGraphics.dispose();
        }
    }

    private void drawTitle(Graphics2D graphics) {
        Font titleFont = getFont().deriveFont(Font.BOLD, 16f);
        graphics.setFont(titleFont);
        graphics.setColor(TEXT_COLOR);
        graphics.drawString("Recent Monthly Spending", 22, 27);
    }

    private void drawBars(Graphics2D graphics) {
        int plotLeft = 46;
        int plotRight = Math.max(plotLeft + 1, getWidth() - 22);
        int plotTop = 54;
        int plotBottom = Math.max(plotTop + 1, getHeight() - 48);
        int plotWidth = plotRight - plotLeft;
        int plotHeight = plotBottom - plotTop;

        graphics.setColor(BASELINE_COLOR);
        graphics.drawLine(plotLeft, plotBottom, plotRight, plotBottom);

        BigDecimal largestValue = largestNonNegativeValue();
        if (monthlyTotals.isEmpty() || largestValue.compareTo(BigDecimal.ZERO) == 0) {
            drawNoDataMessage(graphics, plotLeft, plotTop, plotWidth, plotHeight);
            return;
        }

        int monthCount = monthlyTotals.size();
        double slotWidth = (double) plotWidth / monthCount;
        int barWidth = Math.max(8, (int) Math.min(58, slotWidth * 0.56));
        Font amountFont = getFont().deriveFont(Font.BOLD, 11f);
        Font monthFont = getFont().deriveFont(11f);
        FontMetrics monthMetrics = graphics.getFontMetrics(monthFont);
        int index = 0;

        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyTotals.entrySet()) {
            BigDecimal value = entry.getValue();
            int slotCenter = plotLeft + (int) Math.round(slotWidth * (index + 0.5));
            int barHeight = 0;
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = value.doubleValue() / largestValue.doubleValue();
                barHeight = Math.max(1, (int) Math.round(ratio * (plotHeight - 28)));
            }
            int barX = slotCenter - barWidth / 2;
            int barY = plotBottom - barHeight;

            graphics.setColor(index == monthCount - 1 ? BAR_HIGHLIGHT : BAR_COLOR);
            graphics.fillRoundRect(barX, barY, barWidth, barHeight, 8, 8);

            if (barWidth >= 32 && barHeight > 0) {
                graphics.setFont(amountFont);
                graphics.setColor(TEXT_COLOR);
                String amountText = value.toPlainString();
                int textWidth = graphics.getFontMetrics().stringWidth(amountText);
                graphics.drawString(
                        amountText,
                        slotCenter - textWidth / 2,
                        Math.max(plotTop + 12, barY - 6));
            }

            graphics.setFont(monthFont);
            graphics.setColor(SECONDARY_TEXT);
            String monthText = entry.getKey().format(MONTH_FORMAT);
            graphics.drawString(
                    monthText,
                    slotCenter - monthMetrics.stringWidth(monthText) / 2,
                    plotBottom + 22);
            index++;
        }
    }

    private void drawNoDataMessage(
            Graphics2D graphics, int left, int top, int width, int height) {
        String message = "No spending recorded for these months.";
        graphics.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        graphics.setColor(SECONDARY_TEXT);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(
                message,
                left + Math.max(0, (width - metrics.stringWidth(message)) / 2),
                top + height / 2);
    }

    private BigDecimal largestNonNegativeValue() {
        BigDecimal largestValue = BigDecimal.ZERO;
        for (BigDecimal value : monthlyTotals.values()) {
            if (value.compareTo(largestValue) > 0) {
                largestValue = value;
            }
        }
        return largestValue;
    }

    private static Map<YearMonth, BigDecimal> copyMonthlyTotals(
            Map<YearMonth, BigDecimal> suppliedTotals) {
        Objects.requireNonNull(suppliedTotals, "Monthly chart data is required.");
        LinkedHashMap<YearMonth, BigDecimal> copiedTotals = new LinkedHashMap<>();
        YearMonth previousMonth = null;
        for (Map.Entry<YearMonth, BigDecimal> entry : suppliedTotals.entrySet()) {
            YearMonth month = Objects.requireNonNull(
                    entry.getKey(), "Monthly chart data cannot contain null month keys.");
            BigDecimal total = Objects.requireNonNull(
                    entry.getValue(), "Monthly chart data cannot contain null values.");
            if (previousMonth != null && !month.isAfter(previousMonth)) {
                throw new IllegalArgumentException(
                        "Monthly chart data must be in chronological order.");
            }
            copiedTotals.put(month, total);
            previousMonth = month;
        }
        return Collections.unmodifiableMap(copiedTotals);
    }
}
