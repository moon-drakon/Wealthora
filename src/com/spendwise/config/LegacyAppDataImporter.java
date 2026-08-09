package com.spendwise.config;

import com.spendwise.auth.local.CsvLocalUserRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.SafeFileSupport;
import com.spendwise.service.FinanceWorkspace;
import com.spendwise.service.ManagedDataFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Performs the one-time, user-approved, non-destructive import from the
 * application-data location used by older releases.
 */
public final class LegacyAppDataImporter {

    private static final String MARKER = "appdata-migration-v1.properties";
    private final Path portableDataRoot;
    private final Path legacyApplicationRoot;

    public LegacyAppDataImporter(
            Path portableDataRoot, Path legacyApplicationRoot) {
        this.portableDataRoot = Objects.requireNonNull(portableDataRoot)
                .toAbsolutePath().normalize();
        this.legacyApplicationRoot = Objects.requireNonNull(legacyApplicationRoot)
                .toAbsolutePath().normalize();
    }

    public boolean shouldOfferMigration() {
        return !decisionRecorded()
                && portableStoreIsEmpty()
                && legacyStoreContainsData();
    }

    public synchronized MigrationResult importLegacyData() {
        if (!shouldOfferMigration()) {
            return new MigrationResult(false, 0);
        }
        Path staging = null;
        List<Path> installed = new ArrayList<>();
        try {
            staging = Files.createTempDirectory(
                    portableDataRoot.getParent(), ".wealthora-appdata-import-");
            copyIfPresent(legacyApplicationRoot.resolve("auth"),
                    staging.resolve("auth"));
            copyIfPresent(legacyApplicationRoot.resolve("data"), staging);
            copyIfPresent(legacyApplicationRoot.resolve("backups"),
                    staging.resolve("backups"));
            validate(staging);
            try (var paths = Files.walk(staging)) {
                for (Path source : paths.filter(Files::isRegularFile).toList()) {
                    Path relative = staging.relativize(source);
                    Path destination = portableDataRoot.resolve(relative)
                            .toAbsolutePath().normalize();
                    if (!destination.startsWith(portableDataRoot)
                            || Files.exists(destination)) {
                        throw new IllegalStateException(
                                "Portable data changed while the migration was being prepared.");
                    }
                    SafeFileSupport.write(destination, Files.readAllBytes(source),
                            false, ".wealthora-migration-", "migrated data");
                    installed.add(destination);
                }
            }
            recordDecision("imported");
            return new MigrationResult(true, installed.size());
        } catch (IOException | RuntimeException failure) {
            rollback(installed, failure);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RepositoryException(
                    "Older Wealthora data could not be copied safely.", failure);
        } finally {
            deleteTree(staging);
        }
    }

    public synchronized void declineMigration() {
        if (!decisionRecorded()) {
            recordDecision("skipped");
        }
    }

    private boolean decisionRecorded() {
        return Files.isRegularFile(markerPath());
    }

    private boolean portableStoreIsEmpty() {
        if (Files.notExists(portableDataRoot)) {
            return true;
        }
        try (var paths = Files.walk(portableDataRoot)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .allMatch(name -> name.equals(".wealthora.lock"));
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException(
                    "Portable data could not be inspected safely.", exception);
        }
    }

    private boolean legacyStoreContainsData() {
        if (legacyApplicationRoot.equals(portableDataRoot.getParent())) {
            return false;
        }
        Path users = legacyApplicationRoot.resolve("auth").resolve("users.csv");
        if (Files.isRegularFile(users)) {
            return true;
        }
        Path finance = legacyApplicationRoot.resolve("data");
        return ManagedDataFiles.FILE_NAMES.stream()
                .anyMatch(name -> Files.isRegularFile(finance.resolve(name)))
                || directoryContainsRegularFile(finance.resolve("users"));
    }

    private static boolean directoryContainsRegularFile(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(Files::isRegularFile);
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException(
                    "Older Wealthora data could not be inspected.", exception);
        }
    }

    private static void copyIfPresent(Path source, Path destination)
            throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void validate(Path staging) {
        Path users = staging.resolve("auth").resolve("users.csv");
        if (Files.isRegularFile(users)) {
            new CsvLocalUserRepository(users).findAll();
        }
        validateFinanceDirectory(staging);
        Path userRoot = staging.resolve("users");
        if (Files.isDirectory(userRoot)) {
            try (var directories = Files.list(userRoot)) {
                directories.filter(Files::isDirectory)
                        .forEach(LegacyAppDataImporter::validateFinanceDirectory);
            } catch (IOException exception) {
                throw new RepositoryException(
                        "Older user workspaces could not be validated.", exception);
            }
        }
    }

    private static void validateFinanceDirectory(Path directory) {
        boolean containsFinance = ManagedDataFiles.FILE_NAMES.stream()
                .anyMatch(name -> Files.isRegularFile(directory.resolve(name)));
        if (!containsFinance) {
            return;
        }
        FinanceWorkspace workspace = FinanceWorkspace.overDirectory(directory);
        workspace.categories().findAll();
        workspace.accounts().findAll();
        workspace.accountPreference().findDefaultAccountId();
        workspace.expenses().findAll();
        workspace.income().findAll();
        workspace.transfers().findAll();
        workspace.budgets().findAll();
        workspace.budgetPlans().findAll();
        workspace.recurring().findAll();
        workspace.goals().findAllGoals();
        workspace.debts().findAllDebts();
    }

    private void recordDecision(String decision) {
        String marker = "format=1\nresult=" + decision
                + "\ncompletedAt=" + Instant.now() + "\n";
        SafeFileSupport.write(
                markerPath(),
                marker.getBytes(StandardCharsets.UTF_8), true,
                ".wealthora-migration-marker-", "migration marker");
    }

    private Path markerPath() {
        return portableDataRoot.resolve("settings").resolve(MARKER);
    }

    private static void rollback(List<Path> installed, Throwable failure) {
        for (Path path : installed.stream()
                .sorted(Comparator.reverseOrder()).toList()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException | SecurityException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void deleteTree(Path directory) {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | SecurityException ignored) {
            // Temporary migration files only; existing data is never removed.
        }
    }

    public record MigrationResult(boolean imported, int copiedFileCount) {
    }
}
