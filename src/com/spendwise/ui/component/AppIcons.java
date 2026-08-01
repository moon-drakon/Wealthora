package com.spendwise.ui.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

public final class AppIcons {

    public enum Type {
        DASHBOARD, TRANSACTIONS, EXPENSES, FINANCE, BUDGETS,
        CALENDAR, REPORTS, RECURRING, ANALYTICS, SEARCH, THEME, ADD
    }

    private AppIcons() {
    }

    public static Icon icon(Type type, int size) {
        return new LineIcon(type, size);
    }

    private static final class LineIcon implements Icon {

        private final Type type;
        private final int size;

        private LineIcon(Type type, int size) {
            this.type = type;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D drawing = (Graphics2D) graphics.create();
            try {
                drawing.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                drawing.setColor(component == null
                        ? Color.GRAY : component.getForeground());
                drawing.setStroke(new BasicStroke(
                        Math.max(1.5f, size / 10f),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int left = x + 2;
                int top = y + 2;
                int width = size - 4;
                int height = size - 4;
                paintShape(drawing, left, top, width, height);
            } finally {
                drawing.dispose();
            }
        }

        private void paintShape(
                Graphics2D drawing, int x, int y, int width, int height) {
            switch (type) {
                case DASHBOARD -> {
                    int halfWidth = width / 2 - 1;
                    int halfHeight = height / 2 - 1;
                    drawing.drawRoundRect(x, y, halfWidth, halfHeight, 3, 3);
                    drawing.drawRoundRect(x + halfWidth + 3, y,
                            halfWidth, halfHeight, 3, 3);
                    drawing.drawRoundRect(x, y + halfHeight + 3,
                            halfWidth, halfHeight, 3, 3);
                    drawing.drawRoundRect(x + halfWidth + 3,
                            y + halfHeight + 3, halfWidth, halfHeight, 3, 3);
                }
                case TRANSACTIONS -> {
                    drawing.drawLine(x, y + height / 3, x + width, y + height / 3);
                    drawing.drawLine(x, y + height * 2 / 3,
                            x + width, y + height * 2 / 3);
                    drawing.drawLine(x + width - 4, y + height / 3 - 3,
                            x + width, y + height / 3);
                    drawing.drawLine(x + width - 4, y + height / 3 + 3,
                            x + width, y + height / 3);
                }
                case EXPENSES -> {
                    drawing.drawRoundRect(x + 1, y, width - 2, height, 4, 4);
                    drawing.drawLine(x + width / 3, y + 5,
                            x + width * 2 / 3, y + 5);
                    drawing.drawLine(x + width / 3, y + 10,
                            x + width * 3 / 4, y + 10);
                }
                case FINANCE -> {
                    drawing.drawOval(x, y, width, height);
                    drawing.drawLine(x + width / 2, y + 4,
                            x + width / 2, y + height - 4);
                    drawing.drawArc(x + width / 3, y + 4,
                            width / 3, height / 3, 70, 220);
                    drawing.drawArc(x + width / 3, y + height / 2,
                            width / 3, height / 3, 250, 220);
                }
                case BUDGETS -> {
                    drawing.drawArc(x, y, width, height, 0, 270);
                    drawing.drawLine(x + width / 2, y + height / 2,
                            x + width, y + height / 2);
                    drawing.drawLine(x + width / 2, y + height / 2,
                            x + width / 2, y);
                }
                case CALENDAR -> {
                    drawing.drawRoundRect(x, y + 2, width, height - 2, 3, 3);
                    drawing.drawLine(x, y + height / 3, x + width, y + height / 3);
                    drawing.drawLine(x + width / 3, y, x + width / 3, y + 5);
                    drawing.drawLine(x + width * 2 / 3, y,
                            x + width * 2 / 3, y + 5);
                }
                case REPORTS, ANALYTICS -> {
                    drawing.drawLine(x, y + height, x + width, y + height);
                    drawing.drawLine(x + 2, y + height,
                            x + 2, y + height * 2 / 3);
                    drawing.drawLine(x + width / 2, y + height,
                            x + width / 2, y + height / 3);
                    drawing.drawLine(x + width - 2, y + height,
                            x + width - 2, y);
                }
                case RECURRING -> {
                    drawing.drawArc(x, y, width, height, 35, 255);
                    drawing.drawLine(x + width - 1, y + 2,
                            x + width - 1, y + 7);
                    drawing.drawLine(x + width - 1, y + 2,
                            x + width - 6, y + 2);
                }
                case SEARCH -> {
                    drawing.drawOval(x, y, width * 2 / 3, height * 2 / 3);
                    drawing.drawLine(x + width * 2 / 3, y + height * 2 / 3,
                            x + width, y + height);
                }
                case THEME -> {
                    drawing.drawArc(x, y, width, height, 70, 220);
                    drawing.drawArc(x + width / 3, y,
                            width * 2 / 3, height, 110, 180);
                }
                case ADD -> {
                    drawing.drawLine(x + width / 2, y,
                            x + width / 2, y + height);
                    drawing.drawLine(x, y + height / 2,
                            x + width, y + height / 2);
                }
            }
        }
    }
}
