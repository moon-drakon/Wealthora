package com.spendwise.ui.component;

import java.awt.Component;
import javax.swing.JOptionPane;

public final class ConfirmationDialogs {

    private ConfirmationDialogs() {
    }

    public static boolean confirmDestructive(
            Component owner, String title, String message) {
        return confirm(owner, title, message, JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(
            Component owner, String title, String message, int messageType) {
        return JOptionPane.showConfirmDialog(
                owner,
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                messageType) == JOptionPane.YES_OPTION;
    }

    public static void showError(
            Component owner, String title, RuntimeException exception) {
        String message = exception.getMessage();
        JOptionPane.showMessageDialog(
                owner,
                message == null || message.isBlank()
                        ? "The operation could not be completed safely."
                        : message,
                title,
                JOptionPane.ERROR_MESSAGE);
    }
}
