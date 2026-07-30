package com.spendwise.app;

import com.spendwise.config.AppPaths;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.service.ExpenseService;
import com.spendwise.ui.SpendWiseFrame;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class SpendWiseApplication {

    private SpendWiseApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SpendWiseApplication::startApplication);
    }

    private static void startApplication() {
        applySystemLookAndFeel();
        try {
            Path expenseCsvPath = AppPaths.getExpenseCsvPath();
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(expenseCsvPath);
            ExpenseService expenseService = new ExpenseService(repository);
            SpendWiseFrame frame = new SpendWiseFrame(expenseService);
            frame.setVisible(true);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(
                    null,
                    startupErrorMessage(exception),
                    "SpendWise Could Not Start",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException
                | UnsupportedLookAndFeelException
                | SecurityException exception) {
            // Swing's default look and feel remains available.
        }
    }

    private static String startupErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "SpendWise could not start safely.";
        }
        return "SpendWise could not start: " + message;
    }
}
