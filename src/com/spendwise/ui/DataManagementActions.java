package com.spendwise.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AdvancedReportSnapshot;
import com.spendwise.service.BackupInspection;
import com.spendwise.service.BackupResult;
import com.spendwise.service.BackupService;
import com.spendwise.service.ExportResult;
import com.spendwise.service.ExportService;
import com.spendwise.service.CsvImportService;
import com.spendwise.service.ImportResult;
import com.spendwise.service.JsonBackupService;
import com.spendwise.service.PdfReportService;
import com.spendwise.service.PortfolioAnalyticsSnapshot;
import com.spendwise.service.RestoreResult;
import com.spendwise.validation.ValidationException;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

final class DataManagementActions {

    private final Component owner;
    private final BackupService backupService;
    private final ExportService exportService;
    private final Supplier<AdvancedReportSnapshot> reportSupplier;
    private final Runnable restoreSuccessListener;
    private final JsonBackupService jsonBackupService;
    private final CsvImportService csvImportService;
    private final PdfReportService pdfReportService;
    private final Supplier<PortfolioAnalyticsSnapshot> portfolioSupplier;
    private final Supplier<String> currencySupplier;

    DataManagementActions(
            Component owner,
            BackupService backupService,
            ExportService exportService,
            Supplier<AdvancedReportSnapshot> reportSupplier,
            Runnable restoreSuccessListener) {
        this(owner, backupService, exportService, reportSupplier,
                restoreSuccessListener, null, null, null, null, null);
    }

    DataManagementActions(
            Component owner,
            BackupService backupService,
            ExportService exportService,
            Supplier<AdvancedReportSnapshot> reportSupplier,
            Runnable restoreSuccessListener,
            JsonBackupService jsonBackupService,
            CsvImportService csvImportService,
            PdfReportService pdfReportService,
            Supplier<PortfolioAnalyticsSnapshot> portfolioSupplier,
            Supplier<String> currencySupplier) {
        this.owner = Objects.requireNonNull(owner, "Data-action owner is required.");
        this.backupService = Objects.requireNonNull(
                backupService, "Backup service is required.");
        this.exportService = Objects.requireNonNull(
                exportService, "Export service is required.");
        this.reportSupplier = Objects.requireNonNull(
                reportSupplier, "Report supplier is required.");
        this.restoreSuccessListener = Objects.requireNonNull(
                restoreSuccessListener,
                "Restore success listener is required.");
        this.jsonBackupService = jsonBackupService;
        this.csvImportService = csvImportService;
        this.pdfReportService = pdfReportService;
        this.portfolioSupplier = portfolioSupplier;
        this.currencySupplier = currencySupplier;
    }

    JMenu createMenu() {
        JMenu dataMenu = new JMenu("Data");
        dataMenu.setMnemonic('D');
        dataMenu.add(item("Create Backup...", this::createBackup));
        dataMenu.add(item("Restore Backup...", this::restoreBackup));
        if (jsonBackupService != null) {
            dataMenu.add(item("Create JSON Backup...", this::createJsonBackup));
            dataMenu.add(item("Restore JSON Backup...", this::restoreJsonBackup));
        }
        if (csvImportService != null) {
            dataMenu.add(item("Import " + AppBrand.APP_NAME + " CSV...",
                    this::importCsv));
        }
        dataMenu.addSeparator();
        JMenu exportMenu = new JMenu("Export");
        exportMenu.add(item("Expenses...", () -> export(
                "Export Expenses", "expenses-export.csv",
                exportService::exportExpenses)));
        exportMenu.add(item("Income...", () -> export(
                "Export Income", "income-export.csv",
                exportService::exportIncome)));
        exportMenu.add(item("Transfers...", () -> export(
                "Export Transfers", "transfers-export.csv",
                exportService::exportTransfers)));
        exportMenu.add(item("Account Summary...", () -> export(
                "Export Account Summary", "account-summary.csv",
                exportService::exportAccountSummary)));
        exportMenu.add(item("Current Report...", this::exportCurrentReport));
        if (pdfReportService != null) {
            exportMenu.add(item("Current Report as PDF...", this::exportPdf));
        }
        dataMenu.add(exportMenu);
        return dataMenu;
    }

    private void createJsonBackup() {
        JFileChooser chooser = chooser("Create Versioned JSON Backup",
                AppBrand.JSON_BACKUP_FILE_NAME, "JSON backup", "json");
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath();
        Boolean overwrite = confirmOverwrite(destination);
        if (overwrite == null) return;
        try {
            BackupResult result = jsonBackupService.createBackup(
                    destination, overwrite);
            showInformation("JSON backup created with "
                    + result.includedFiles().size() + " managed file(s):\n"
                    + result.destination());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void restoreJsonBackup() {
        JFileChooser chooser = chooser("Select Versioned JSON Backup", null,
                "JSON backup", "json");
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        try {
            BackupInspection inspection = jsonBackupService.inspectBackup(source);
            int answer = JOptionPane.showConfirmDialog(owner,
                    "Validated format version 1 backup from "
                    + inspection.createdAt() + ".\nRestoring will first preserve "
                    + "current data in a safety ZIP. Continue?",
                    "Confirm JSON Restore", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            RestoreResult result = jsonBackupService.restoreBackup(source);
            restoreSuccessListener.run();
            showInformation("JSON restore completed for "
                    + result.restoredFiles().size() + " managed file(s)."
                    + result.safetyBackup().map(path ->
                        "\nSafety backup: " + path).orElse(""));
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void importCsv() {
        JFileChooser chooser = chooser(
                "Import " + AppBrand.APP_NAME + " CSV Export", null,
                "CSV file", "csv");
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        int answer = JOptionPane.showConfirmDialog(owner,
                "The complete file will be validated and a safety backup will "
                + "be created before any rows are imported. Continue?",
                "Confirm CSV Import", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            ImportResult result = csvImportService.importFile(source);
            restoreSuccessListener.run();
            showInformation("Imported " + result.importedCount() + " "
                    + result.recordType() + " record(s).\nSafety backup: "
                    + result.safetyBackup());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void exportPdf() {
        PortfolioAnalyticsSnapshot snapshot = portfolioSupplier == null
                ? null : portfolioSupplier.get();
        if (snapshot == null) {
            showError("Run a valid report before exporting its PDF summary.");
            return;
        }
        JFileChooser chooser = chooser("Export Current PDF Report",
                "financial-report.pdf", "PDF file", "pdf");
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath();
        Boolean overwrite = confirmOverwrite(destination);
        if (overwrite == null) return;
        try {
            ExportResult result = pdfReportService.export(destination, overwrite,
                    snapshot, currencySupplier.get());
            showInformation("PDF report exported to:\n" + result.destination());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void createBackup() {
        JFileChooser chooser = chooser(
                "Create " + AppBrand.APP_NAME + " Backup",
                AppBrand.BACKUP_FILE_NAME, "ZIP backup", "zip");
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = chooser.getSelectedFile().toPath();
        Boolean overwrite = confirmOverwrite(destination);
        if (overwrite == null) {
            return;
        }
        try {
            BackupResult result = backupService.createBackup(
                    destination, overwrite);
            showInformation(
                    "Backup created with " + result.includedFiles().size()
                    + " managed data file(s):\n" + result.destination());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void restoreBackup() {
        JFileChooser chooser = chooser(
                "Select " + AppBrand.APP_NAME + " Backup",
                null, "ZIP backup", "zip");
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        try {
            BackupInspection inspection = backupService.inspectBackup(source);
            String files = inspection.includedFiles().isEmpty()
                    ? "(no managed files)"
                    : String.join("\n", inspection.includedFiles());
            int answer = JOptionPane.showConfirmDialog(
                    owner,
                    "Backup created: " + inspection.createdAt()
                    + "\n\nManaged files to restore:\n" + files
                    + "\n\nCurrent managed data will receive a safety backup. Continue?",
                    "Confirm Restore",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            RestoreResult result = backupService.restoreBackup(source);
            restoreSuccessListener.run();
            showInformation(
                    "Restore completed for " + result.restoredFiles().size()
                    + " managed data file(s)."
                    + result.safetyBackup()
                            .map(path -> "\nSafety backup: " + path)
                            .orElse(""));
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private void exportCurrentReport() {
        AdvancedReportSnapshot snapshot = reportSupplier.get();
        if (snapshot == null) {
            showError("Run a valid report before exporting it.");
            return;
        }
        export(
                "Export Current Report",
                "financial-report.csv",
                (path, overwrite) -> exportService.exportReport(
                    path, overwrite, snapshot));
    }

    private void export(
            String title,
            String suggestedName,
            ExportOperation operation) {
        JFileChooser chooser = chooser(
                title, suggestedName, "CSV file", "csv");
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = chooser.getSelectedFile().toPath();
        Boolean overwrite = confirmOverwrite(destination);
        if (overwrite == null) {
            return;
        }
        try {
            ExportResult result = operation.export(destination, overwrite);
            showInformation(
                    "Exported " + result.rowCount() + " row(s) to:\n"
                    + result.destination());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private Boolean confirmOverwrite(Path destination) {
        if (Files.notExists(destination)) {
            return false;
        }
        int answer = JOptionPane.showConfirmDialog(
                owner,
                "The selected file already exists. Replace it?\n" + destination,
                "Confirm Replace",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION ? true : null;
    }

    private static JFileChooser chooser(
            String title,
            String suggestedName,
            String description,
            String extension) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(
                description, extension));
        if (suggestedName != null) {
            chooser.setSelectedFile(new java.io.File(suggestedName));
        }
        return chooser;
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void showInformation(String message) {
        JOptionPane.showMessageDialog(
                owner, message, AppBrand.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                owner,
                message,
                "Data Operation Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The data operation could not be completed safely."
                : message;
    }

    @FunctionalInterface
    private interface ExportOperation {

        ExportResult export(Path destination, boolean allowOverwrite);
    }
}
