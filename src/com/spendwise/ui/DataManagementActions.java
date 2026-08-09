package com.spendwise.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.config.AppPaths;
import com.spendwise.imports.ForeignBackupDetector;
import com.spendwise.imports.ForeignBackupFormat;
import com.spendwise.imports.MoneyManagerImport;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AdvancedReportSnapshot;
import com.spendwise.service.BackupInspection;
import com.spendwise.service.BackupResult;
import com.spendwise.service.BackupService;
import com.spendwise.service.ExportResult;
import com.spendwise.service.ExportService;
import com.spendwise.service.CsvImportService;
import com.spendwise.service.ImportPreview;
import com.spendwise.service.ImportReport;
import com.spendwise.service.ImportResult;
import com.spendwise.service.JsonBackupService;
import com.spendwise.service.PdfReportService;
import com.spendwise.service.PortfolioAnalyticsSnapshot;
import com.spendwise.service.RestoreResult;
import com.spendwise.service.WorkspacePortabilityService;
import com.spendwise.validation.ValidationException;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
    private final WorkspacePortabilityService portability;
    private final boolean showLocalWorkspaceImport;

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
        this(owner, backupService, exportService, reportSupplier,
                restoreSuccessListener, jsonBackupService, csvImportService,
                pdfReportService, portfolioSupplier, currencySupplier,
                null, false);
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
            Supplier<String> currencySupplier,
            WorkspacePortabilityService portability,
            boolean showLocalWorkspaceImport) {
        this.owner = Objects.requireNonNull(owner, "Data-action owner is required.");
        this.backupService = backupService;
        this.exportService = exportService;
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
        this.portability = portability;
        this.showLocalWorkspaceImport = showLocalWorkspaceImport;
    }

    JMenu createMenu() {
        JMenu dataMenu = new JMenu("Data");
        dataMenu.setMnemonic('D');
        if (portability != null) {
            dataMenu.add(item(
                    "Export Full " + AppBrand.APP_NAME + " Backup (.zip)",
                    this::createBackup));
            dataMenu.add(item(
                    "Import " + AppBrand.APP_NAME + " Backup (.zip)",
                    this::restoreBackup));
        }
        if (jsonBackupService != null) {
            dataMenu.add(item("Create JSON Backup...", this::createJsonBackup));
            dataMenu.add(item("Restore JSON Backup...", this::restoreJsonBackup));
        }
        if (exportService != null || csvImportService != null) {
            dataMenu.addSeparator();
        }
        if (exportService != null) {
            dataMenu.add(item("Export Transactions (.csv)", () -> export(
                    "Export Transactions", "transactions-export.csv",
                    exportService::exportTransactions)));
        }
        if (csvImportService != null) {
            dataMenu.add(item("Import Transactions (.csv)", this::importCsv));
        }
        if (portability != null) {
            dataMenu.addSeparator();
            JMenu importFromAnotherApp = new JMenu("Import from Another App");
            if (showLocalWorkspaceImport) {
                importFromAnotherApp.add(item(
                        "Import Existing Local " + AppBrand.APP_NAME + " Data",
                        this::importLocalWorkspace));
            }
            importFromAnotherApp.add(item(
                    "Import Money Manager Backup",
                    this::importMoneyManagerBackup));
            dataMenu.add(importFromAnotherApp);
        }
        if (exportService != null) {
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
        }
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

    /**
     * Writes the whole current local workspace to one ZIP package via
     * {@link #portability}.
     */
    void createBackup() {
        JFileChooser chooser = chooser(
                "Export " + AppBrand.APP_NAME + " Backup",
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
            BackupResult result = portability.exportBackup(destination, overwrite);
            showInformation(
                    "Backup created with " + result.includedFiles().size()
                    + " file(s):\n" + result.destination());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    /**
     * Merges a ZIP backup package into the current workspace via
     * {@link #portability}, previewing what will change before anything is
     * written and writing a safety backup of the current data first.
     */
    void restoreBackup() {
        JFileChooser chooser = chooser(
                "Select " + AppBrand.APP_NAME + " Backup",
                null, "ZIP backup", "zip");
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        try {
            ImportPreview preview = portability.previewBackup(source);
            if (!confirmImport(preview.displayText())) {
                return;
            }
            Path safety = portability.suggestSafetyBackupPath(source.getParent());
            ImportReport report = portability.importBackup(source, safety);
            restoreSuccessListener.run();
            showInformation(report.displayText());
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    /**
     * Imports an existing offline {@code Wealthora} data folder into the
     * current workspace. The target is the signed-in account bound to
     * {@link #portability}, so the desktop cannot write into another user's
     * workspace.
     */
    private void importLocalWorkspace() {
        Path legacyDirectory = AppPaths.getLegacyDataDirectory();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(
                "Select Existing " + AppBrand.APP_NAME + " Data Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (Files.isDirectory(legacyDirectory)) {
            chooser.setCurrentDirectory(legacyDirectory.toFile());
        }
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path folder = chooser.getSelectedFile().toPath();
        try {
            ImportPreview preview = portability.previewLocalWorkspace(folder);
            if (!confirmImport(preview.displayText())) {
                return;
            }
            ImportReport report = portability.importLocalWorkspace(
                    folder, portability.suggestSafetyBackupPath(folder));
            restoreSuccessListener.run();
            showInformation(report.displayText());
        } catch (RuntimeException exception) {
            showError(safeMessage(exception));
        }
    }

    /**
     * Reads a Money Manager backup file (a database, possibly inside a ZIP)
     * and merges the records it can map into the current workspace.
     */
    private void importMoneyManagerBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Money Manager Backup File");
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        ForeignBackupFormat format;
        try {
            format = ForeignBackupDetector.detect(source);
        } catch (RuntimeException exception) {
            showError(safeMessage(exception));
            return;
        }
        if (!format.isImportable()) {
            showError(format.notes().isEmpty()
                    ? format.description()
                    : format.description() + "\n\n"
                            + String.join("\n", format.notes()));
            return;
        }
        Path staging;
        try {
            staging = Files.createTempDirectory("wealthora-mm-import-");
        } catch (IOException exception) {
            showError("A temporary working folder could not be created.");
            return;
        }
        try {
            MoneyManagerImport.Result result =
                    MoneyManagerImport.read(source, staging);
            ImportPreview preview = portability.previewWorkspace(
                    result.workspace(),
                    "Money Manager backup (" + result.currencyCode() + ")");
            StringBuilder message = new StringBuilder(preview.displayText());
            if (!result.warnings().isEmpty()) {
                message.append("\n\nMoney Manager notes:\n");
                result.warnings().forEach(warning ->
                        message.append("  • ").append(warning).append('\n'));
            }
            if (!confirmImport(message.toString())) {
                return;
            }
            ImportReport report = portability.importWorkspace(result.workspace(),
                    portability.suggestSafetyBackupPath(source.getParent()));
            restoreSuccessListener.run();
            StringBuilder resultMessage = new StringBuilder(report.displayText());
            if (result.skippedRecords() > 0) {
                resultMessage.append("\n\nMoney Manager also skipped ")
                        .append(result.skippedRecords())
                        .append(" record(s) that could not be mapped.");
            }
            showInformation(resultMessage.toString());
        } catch (RuntimeException exception) {
            showError(safeMessage(exception));
        } finally {
            deleteRecursively(staging);
        }
    }

    private boolean confirmImport(String message) {
        Object[] options = {"Import", "Cancel"};
        int answer = JOptionPane.showOptionDialog(owner, message,
                "Confirm Import", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return answer == 0;
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | SecurityException ignored) {
            // Temporary files only; the operating system reclaims them.
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
