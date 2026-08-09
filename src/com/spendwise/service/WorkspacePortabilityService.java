package com.spendwise.service;

import com.spendwise.config.AppBrand;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backup, restore, and migration for a finance workspace.
 *
 * <p>Everything is read and written through {@link FinanceWorkspace}, so the
 * same package format and the same safety rules apply to every project-local
 * user workspace. The active local session determines whose folder is open.
 *
 * <p>Restores merge: records that are already present are skipped and nothing is
 * deleted. A safety backup of the current workspace is written before the first
 * change, and a failure part-way through removes the transactions this run
 * added.
 */
public final class WorkspacePortabilityService {

    private static final DateTimeFormatter SAFETY_NAME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final FinanceWorkspace workspace;
    private final Clock clock;

    public WorkspacePortabilityService(FinanceWorkspace workspace) {
        this(workspace, Clock.systemUTC());
    }

    public WorkspacePortabilityService(
            FinanceWorkspace workspace, Clock clock) {
        this.workspace = Objects.requireNonNull(
                workspace, "Finance workspace is required.");
        this.clock = Objects.requireNonNull(clock, "Backup clock is required.");
    }

    /**
     * Writes the whole workspace to one backup package and checks that the
     * result can be read back before reporting success.
     */
    public BackupResult exportBackup(Path destination, boolean allowOverwrite) {
        Path target = Objects.requireNonNull(
                destination, "Backup destination is required.")
                .toAbsolutePath().normalize();
        Path staging = createTemporaryDirectory("wealthora-backup-");
        try {
            new WorkspaceTransfer(
                    workspace, FinanceWorkspace.overDirectory(staging), false)
                    .run();
            BackupService packager = new BackupService(staging);
            BackupResult result = packager.createBackup(target, allowOverwrite);
            packager.inspectBackup(result.destination());
            return result;
        } finally {
            deleteDirectory(staging);
        }
    }

    /** Reads a backup package and reports what importing it would change. */
    public ImportPreview previewBackup(Path source) {
        Path archive = requireExistingFile(source, "backup");
        Path staging = createTemporaryDirectory("wealthora-preview-");
        try {
            BackupInspection inspection = new BackupService(staging)
                    .extractTo(archive, staging);
            ImportPreview preview = preview(
                    FinanceWorkspace.overDirectory(staging),
                    archive.getFileName().toString(),
                    Optional.of(inspection.createdAt()));
            return preview;
        } finally {
            deleteDirectory(staging);
        }
    }

    /**
     * Merges a backup package into this workspace after writing a safety backup
     * of the current data.
     */
    public ImportReport importBackup(Path source, Path safetyDestination) {
        Path archive = requireExistingFile(source, "backup");
        Path staging = createTemporaryDirectory("wealthora-restore-");
        try {
            new BackupService(staging).extractTo(archive, staging);
            return merge(FinanceWorkspace.overDirectory(staging),
                    safetyDestination);
        } finally {
            deleteDirectory(staging);
        }
    }

    /** Reports what importing an existing local workspace folder would change. */
    public ImportPreview previewLocalWorkspace(Path localDataDirectory) {
        Path directory = requireExistingDirectory(localDataDirectory);
        return preview(FinanceWorkspace.overDirectory(directory),
                "Existing " + AppBrand.APP_NAME + " data on this computer",
                Optional.empty());
    }

    /**
     * Merges an existing local workspace folder into this workspace. This is the
     * supported path for merging reviewed local records into the signed-in
     * user's project-local workspace.
     */
    public ImportReport importLocalWorkspace(
            Path localDataDirectory, Path safetyDestination) {
        Path directory = requireExistingDirectory(localDataDirectory);
        return merge(FinanceWorkspace.overDirectory(directory),
                safetyDestination);
    }

    /** Reports what importing an already-parsed foreign workspace would change. */
    public ImportPreview previewWorkspace(
            FinanceWorkspace incoming, String description) {
        return preview(incoming, description, Optional.empty());
    }

    /** Merges an already-parsed foreign workspace into this workspace. */
    public ImportReport importWorkspace(
            FinanceWorkspace incoming, Path safetyDestination) {
        return merge(Objects.requireNonNull(
                incoming, "Incoming workspace is required."), safetyDestination);
    }

    /**
     * Chooses an unused safety-backup filename beside {@code directory}. Callers
     * pass the folder the user selected so the safety copy stays with the file
     * they are working on.
     */
    public Path suggestSafetyBackupPath(Path directory) {
        Path parent = Objects.requireNonNull(
                directory, "Safety backup directory is required.")
                .toAbsolutePath().normalize();
        String base = AppBrand.APP_NAME + "-safety-"
                + SAFETY_NAME.format(clock.instant());
        for (int suffix = 0; suffix < 1000; suffix++) {
            Path candidate = parent
                    .resolve(base + (suffix == 0 ? "" : "-" + suffix) + ".zip")
                    .toAbsolutePath().normalize();
            if (Files.notExists(candidate)) {
                return candidate;
            }
        }
        throw new ValidationException(
                "Could not choose a unique safety-backup filename.");
    }

    // ---------------------------------------------------------------- internal

    private ImportPreview preview(
            FinanceWorkspace incoming,
            String description,
            Optional<java.time.Instant> createdAt) {
        WorkspaceTransfer transfer =
                new WorkspaceTransfer(incoming, workspace, true);
        transfer.run();
        List<String> warnings = new java.util.ArrayList<>(transfer.warnings());
        transfer.failures().forEach(issue -> warnings.add(issue.toString()));
        return new ImportPreview(description, createdAt, transfer.imported(),
                transfer.skipped(), warnings);
    }

    private ImportReport merge(
            FinanceWorkspace incoming, Path safetyDestination) {
        Optional<Path> safetyBackup = Optional.empty();
        if (safetyDestination != null) {
            safetyBackup = Optional.of(
                    exportBackup(safetyDestination, false).destination());
        }
        WorkspaceTransfer transfer =
                new WorkspaceTransfer(incoming, workspace, false);
        try {
            transfer.run();
        } catch (RuntimeException fatal) {
            transfer.compensate();
            throw fatal;
        }
        return new ImportReport(transfer.imported(), transfer.skipped(),
                transfer.failures(), transfer.warnings(), safetyBackup);
    }

    private static Path requireExistingFile(Path source, String description) {
        Path path = Objects.requireNonNull(
                source, "Import source is required.")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new ValidationException(
                    "Select an existing " + description + " file.");
        }
        return path;
    }

    private static Path requireExistingDirectory(Path source) {
        Path path = Objects.requireNonNull(
                source, "Workspace folder is required.")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new ValidationException(
                    "No " + AppBrand.APP_NAME
                    + " data was found in the selected folder.");
        }
        return path;
    }

    private static Path createTemporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException(
                    "Could not prepare a temporary working folder.", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
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
}
