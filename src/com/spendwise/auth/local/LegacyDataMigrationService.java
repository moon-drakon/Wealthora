package com.spendwise.auth.local;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.audit.AuditAction;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.auth.audit.AuditRepository;
import com.spendwise.service.BackupService;
import com.spendwise.service.ManagedDataFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class LegacyDataMigrationService {

    private static final String MARKER = ".ownership-migration-v1";
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path legacyDirectory;
    private final Path backupDirectory;
    private final AuditRepository auditRepository;
    private final Clock clock;

    public LegacyDataMigrationService(
            Path legacyDirectory,
            Path backupDirectory,
            AuditRepository auditRepository) {
        this(legacyDirectory, backupDirectory, auditRepository,
                Clock.systemUTC());
    }

    LegacyDataMigrationService(
            Path legacyDirectory,
            Path backupDirectory,
            AuditRepository auditRepository,
            Clock clock) {
        this.legacyDirectory = normalized(legacyDirectory, "Legacy data");
        this.backupDirectory = normalized(backupDirectory, "Backup");
        this.auditRepository = Objects.requireNonNull(
                auditRepository, "Audit repository is required.");
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
    }

    public synchronized MigrationResult assignToFirstOwner(
            String ownerUserIdentifier, Path ownerDirectory) {
        String ownerId = Objects.requireNonNull(ownerUserIdentifier).strip();
        Path target = normalized(ownerDirectory, "Owner data");
        if (Files.isRegularFile(target.resolve(MARKER))) {
            return new MigrationResult(false, 0, null);
        }
        List<String> existing = ManagedDataFiles.FILE_NAMES.stream()
                .filter(name -> Files.isRegularFile(legacyDirectory.resolve(name)))
                .toList();
        if (existing.isEmpty()) {
            createEmptyWorkspace(target, ownerId);
            return new MigrationResult(true, 0, null);
        }
        if (Files.exists(target)) {
            throw new AuthException(
                    "Owner workspace migration is incomplete; the existing workspace was not overwritten.");
        }

        Path backup = backupDirectory.resolve("pre-owner-assignment-"
                + BACKUP_TIME.format(clock.instant()) + ".zip");
        new BackupService(legacyDirectory).createBackup(backup, false);
        auditRepository.append(new AuditEvent(clock.instant(), ownerId,
                AuditAction.SAFETY_BACKUP_CREATED, ownerId, "SUCCESS",
                "Created before legacy finance ownership assignment."));

        Path usersDirectory = target.getParent();
        Path staging = null;
        try {
            Files.createDirectories(usersDirectory);
            staging = Files.createTempDirectory(
                    usersDirectory, ".owner-migration-");
            for (String name : existing) {
                Files.copy(legacyDirectory.resolve(name), staging.resolve(name));
            }
            Files.writeString(staging.resolve(MARKER),
                    "ownerUserId=" + ownerId + "\nassignedAt="
                            + clock.instant() + "\n",
                    StandardCharsets.UTF_8);
            moveDirectory(staging, target);
            staging = null;
        } catch (IOException | SecurityException exception) {
            deleteStaging(staging);
            throw new AuthException(
                    "Legacy finance data could not be assigned safely.",
                    exception);
        }
        auditRepository.append(new AuditEvent(clock.instant(), ownerId,
                AuditAction.LEGACY_DATA_ASSIGNED, ownerId, "SUCCESS",
                "Copied " + existing.size()
                        + " managed files; legacy originals retained."));
        return new MigrationResult(true, existing.size(), backup);
    }

    private void createEmptyWorkspace(Path target, String ownerId) {
        try {
            Files.createDirectories(target);
            Files.writeString(target.resolve(MARKER),
                    "ownerUserId=" + ownerId + "\nassignedAt="
                            + clock.instant() + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw new AuthException(
                    "The owner finance workspace could not be prepared.",
                    exception);
        }
    }

    private static void moveDirectory(Path source, Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteStaging(Path staging) {
        if (staging == null || Files.notExists(staging)) return;
        try (var files = Files.walk(staging)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; no existing finance data is touched.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private static Path normalized(Path path, String description) {
        return Objects.requireNonNull(path, description + " path is required.")
                .toAbsolutePath().normalize();
    }

    public record MigrationResult(
            boolean assigned, int copiedFileCount, Path safetyBackup) {
    }
}
