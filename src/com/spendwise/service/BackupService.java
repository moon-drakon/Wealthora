package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvAccountPreferenceRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvBudgetPlanRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvDebtRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvSavingsGoalRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupService {

    public static final String MANIFEST_NAME = "manifest.txt";
    public static final String FORMAT_VERSION = "1";
    public static final String APPLICATION_NAME = "SpendWise Expense Tracker";
    private static final long MAX_ENTRY_BYTES = 50L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L;
    private static final DateTimeFormatter SAFETY_NAME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private final Path dataDirectory;
    private final Clock clock;

    public BackupService(Path dataDirectory) {
        this(dataDirectory, Clock.systemUTC());
    }

    BackupService(Path dataDirectory, Clock clock) {
        this.dataDirectory = Objects.requireNonNull(
                dataDirectory, "Application data directory is required.")
                .toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "Backup clock is required.");
    }

    public BackupResult createBackup(
            Path destination, boolean allowOverwrite) {
        Path target = validateDestination(destination);
        LinkedHashMap<String, byte[]> files = readCurrentData();
        Instant createdAt = clock.instant();
        byte[] archive = buildArchive(files, createdAt);
        SafeFileSupport.write(
                target,
                archive,
                allowOverwrite,
                ".spendwise-backup-",
                "backup");
        return new BackupResult(target, createdAt, List.copyOf(files.keySet()));
    }

    public BackupInspection inspectBackup(Path source) {
        return validateBackup(source).inspection();
    }

    public RestoreResult restoreBackup(Path source) {
        ValidatedBackup backup = validateBackup(source);
        LinkedHashMap<String, byte[]> original = readCurrentData();
        Optional<Path> safetyBackup = Optional.empty();
        if (!original.isEmpty()) {
            Path safetyPath = nextSafetyBackupPath(
                    backup.inspection().source().getParent());
            safetyBackup = Optional.of(
                    createBackup(safetyPath, false).destination());
        }
        restoreTransaction(backup.files(), original);
        return new RestoreResult(
                backup.inspection().includedFiles(), safetyBackup);
    }

    private Path validateDestination(Path destination) {
        Path target = Objects.requireNonNull(
                destination, "Backup destination is required.")
                .toAbsolutePath().normalize();
        if (target.startsWith(dataDirectory)) {
            throw new ValidationException(
                    "Backups must be stored outside the application data directory.");
        }
        return target;
    }

    private LinkedHashMap<String, byte[]> readCurrentData() {
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        for (String name : ManagedDataFiles.FILE_NAMES) {
            Path path = dataDirectory.resolve(name);
            try {
                if (Files.notExists(path)) {
                    continue;
                }
                if (!Files.isRegularFile(path)) {
                    throw new RepositoryException(
                            "Managed data path is not a regular file: " + name);
                }
                files.put(name, Files.readAllBytes(path));
            } catch (IOException | SecurityException exception) {
                throw new RepositoryException(
                        "Could not read managed data file " + name + ".",
                        exception);
            }
        }
        return files;
    }

    private static byte[] buildArchive(
            LinkedHashMap<String, byte[]> files, Instant createdAt) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            putEntry(zip, MANIFEST_NAME,
                    manifest(files.keySet(), createdAt)
                            .getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                putEntry(zip, entry.getKey(), entry.getValue());
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new RepositoryException(
                    "Could not prepare backup archive.", exception);
        }
    }

    private static void putEntry(
            ZipOutputStream zip, String name, byte[] content)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static String manifest(Set<String> names, Instant createdAt) {
        return "formatVersion=" + FORMAT_VERSION + "\n"
                + "application=" + APPLICATION_NAME + "\n"
                + "createdAt=" + createdAt + "\n"
                + "files=" + String.join(";", names) + "\n";
    }

    private ValidatedBackup validateBackup(Path source) {
        Path archive = Objects.requireNonNull(
                source, "Backup source is required.")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(archive)) {
            throw new ValidationException(
                    "Select an existing SpendWise backup file.");
        }
        LinkedHashMap<String, byte[]> entries = readArchive(archive);
        byte[] manifestBytes = entries.remove(MANIFEST_NAME);
        if (manifestBytes == null) {
            throw new ValidationException(
                    "Backup manifest is missing.");
        }
        ManifestValues values = parseManifest(
                new String(manifestBytes, StandardCharsets.UTF_8));
        if (!entries.keySet().equals(new java.util.LinkedHashSet<>(
                values.files()))) {
            throw new ValidationException(
                    "Backup contents do not match the manifest.");
        }
        validateManagedCsvFiles(entries);
        return new ValidatedBackup(
                new BackupInspection(
                        archive, values.createdAt(), values.files()),
                entries);
    }

    private static void validateManagedCsvFiles(
            LinkedHashMap<String, byte[]> entries) {
        if (entries.isEmpty()) {
            return;
        }
        Path validationDirectory = null;
        Throwable validationFailure = null;
        try {
            validationDirectory = Files.createTempDirectory(
                    "spendwise-backup-validation-");
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                Files.write(
                        validationDirectory.resolve(entry.getKey()),
                        entry.getValue());
            }
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(
                            validationDirectory.resolve("categories.csv")));
            AccountService accounts = new AccountService(
                    new CsvAccountRepository(
                            validationDirectory.resolve("accounts.csv")),
                    new CsvAccountPreferenceRepository(
                            validationDirectory.resolve(
                                "account-settings.csv")));
            categories.listAllCategories();
            accounts.listAllAccounts();
            accounts.getDefaultAccount();
            new CsvExpenseRepository(
                    validationDirectory.resolve("expenses.csv"),
                    categories::resolveCategory,
                    accounts::resolveAccount).findAll();
            new CsvIncomeRepository(
                    validationDirectory.resolve("income.csv"),
                    accounts::resolveAccount).findAll();
            new CsvTransferRepository(
                    validationDirectory.resolve("transfers.csv"),
                    accounts::resolveAccount).findAll();
            new CsvRecurringEntryRepository(
                    validationDirectory.resolve("recurring.csv"),
                    categories::resolveCategory,
                    accounts::resolveAccount).findAll();
            new CsvBudgetRepository(
                    validationDirectory.resolve("budgets.csv"),
                    categories::resolveCategory)
                    .isCategoryReferenced(Category.OTHER);
            new CsvBudgetPlanRepository(
                    validationDirectory.resolve("budget-plans.csv"),
                    categories::resolveCategory).findAll();
            new CsvSavingsGoalRepository(
                    validationDirectory.resolve("savings-goals.csv"),
                    accounts::resolveAccount).findAllGoals();
            new CsvDebtRepository(
                    validationDirectory.resolve("debts.csv")).findAllDebts();
        } catch (RuntimeException | IOException exception) {
            ValidationException failure = new ValidationException(
                    "Backup contains invalid managed CSV data: "
                    + safeMessage(exception));
            validationFailure = failure;
            throw failure;
        } finally {
            deleteValidationDirectory(
                    validationDirectory, validationFailure);
        }
    }

    private static void deleteValidationDirectory(
            Path directory, Throwable originalFailure) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | SecurityException cleanupFailure) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(cleanupFailure);
                return;
            }
            throw new RepositoryException(
                    "Could not remove temporary backup validation files.",
                    cleanupFailure);
        }
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "invalid content"
                : message;
    }

    private static LinkedHashMap<String, byte[]> readArchive(Path archive) {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        long totalBytes = 0L;
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                validateEntryName(name, entry.isDirectory());
                if (entries.containsKey(name)) {
                    throw new ValidationException(
                            "Backup contains a duplicate entry: " + name);
                }
                byte[] content = readLimited(zip, name);
                totalBytes += content.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new ValidationException(
                            "Backup uncompressed data is too large.");
                }
                entries.put(name, content);
                zip.closeEntry();
            }
        } catch (ZipException exception) {
            throw new ValidationException(
                    "Backup ZIP is corrupted or unsupported.");
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException(
                    "Could not read backup archive.", exception);
        }
        return entries;
    }

    private static byte[] readLimited(ZipInputStream zip, String name)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long count = 0L;
        while ((read = zip.read(buffer)) >= 0) {
            count += read;
            if (count > MAX_ENTRY_BYTES) {
                throw new ValidationException(
                        "Backup entry is too large: " + name);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateEntryName(String name, boolean directory) {
        if (directory
                || name == null
                || name.isBlank()
                || name.contains("/")
                || name.contains("\\")
                || name.equals(".")
                || name.equals("..")
                || (!name.equals(MANIFEST_NAME)
                    && !ManagedDataFiles.FILE_NAMES.contains(name))) {
            throw new ValidationException(
                    "Backup contains an unsafe or unsupported entry: " + name);
        }
    }

    private static ManifestValues parseManifest(String text) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : text.lines().toList()) {
            int separator = line.indexOf('=');
            if (separator <= 0 || values.putIfAbsent(
                    line.substring(0, separator),
                    line.substring(separator + 1)) != null) {
                throw new ValidationException("Backup manifest is malformed.");
            }
        }
        if (!values.keySet().equals(Set.of(
                "formatVersion", "application", "createdAt", "files"))) {
            throw new ValidationException(
                    "Backup manifest fields are unsupported or incomplete.");
        }
        if (!FORMAT_VERSION.equals(values.get("formatVersion"))) {
            throw new ValidationException(
                    "Backup format version is not supported.");
        }
        if (!APPLICATION_NAME.equals(values.get("application"))) {
            throw new ValidationException(
                    "Backup belongs to a different application.");
        }
        Instant createdAt;
        try {
            createdAt = Instant.parse(values.get("createdAt"));
        } catch (RuntimeException exception) {
            throw new ValidationException(
                    "Backup creation timestamp is invalid.");
        }
        List<String> files = new ArrayList<>();
        String fileText = values.get("files");
        if (!fileText.isEmpty()) {
            Set<String> unique = new HashSet<>();
            for (String name : fileText.split(";", -1)) {
                if (!ManagedDataFiles.FILE_NAMES.contains(name)
                        || !unique.add(name)) {
                    throw new ValidationException(
                            "Backup manifest contains an invalid filename.");
                }
                files.add(name);
            }
        }
        List<String> expectedOrder = ManagedDataFiles.FILE_NAMES.stream()
                .filter(files::contains)
                .toList();
        if (!files.equals(expectedOrder)) {
            throw new ValidationException(
                    "Backup manifest filenames are not in the supported order.");
        }
        return new ManifestValues(createdAt, List.copyOf(files));
    }

    private Path nextSafetyBackupPath(Path directory) {
        Path parent = directory == null
                ? dataDirectory.getParent()
                : directory;
        if (parent == null) {
            throw new ValidationException(
                    "A safety-backup directory is unavailable.");
        }
        String base = "SpendWise-safety-"
                + SAFETY_NAME.format(clock.instant());
        for (int suffix = 0; suffix < 1000; suffix++) {
            String name = base + (suffix == 0 ? "" : "-" + suffix) + ".zip";
            Path candidate = parent.resolve(name).toAbsolutePath().normalize();
            if (Files.notExists(candidate)) {
                return candidate;
            }
        }
        throw new ValidationException(
                "Could not choose a unique safety-backup filename.");
    }

    private void restoreTransaction(
            LinkedHashMap<String, byte[]> restored,
            LinkedHashMap<String, byte[]> original) {
        boolean dataDirectoryExisted = Files.exists(dataDirectory);
        LinkedHashMap<String, Path> staged = new LinkedHashMap<>();
        Throwable restoreFailure = null;
        try {
            for (Map.Entry<String, byte[]> entry : restored.entrySet()) {
                Path destination = dataDirectory.resolve(entry.getKey());
                staged.put(entry.getKey(), SafeFileSupport.createTemporary(
                        destination,
                        entry.getValue(),
                        ".spendwise-restore-",
                        "restored " + entry.getKey()));
            }
            for (String name : ManagedDataFiles.FILE_NAMES) {
                Path destination = dataDirectory.resolve(name);
                Path temporary = staged.get(name);
                if (temporary != null) {
                    SafeFileSupport.move(temporary, destination, true);
                    staged.remove(name);
                } else {
                    Files.deleteIfExists(destination);
                }
            }
        } catch (IOException | RuntimeException failure) {
            rollback(original, dataDirectoryExisted, failure);
            if (failure instanceof RuntimeException runtime) {
                restoreFailure = runtime;
                throw runtime;
            }
            RepositoryException wrapped = new RepositoryException(
                    "Restore failed; original data was recovered.", failure);
            restoreFailure = wrapped;
            throw wrapped;
        } finally {
            deleteStagedFiles(staged.values(), restoreFailure);
        }
    }

    private static void deleteStagedFiles(
            java.util.Collection<Path> stagedFiles,
            Throwable originalFailure) {
        RuntimeException cleanupFailure = null;
        for (Path path : stagedFiles) {
            try {
                SafeFileSupport.deleteTemporary(path);
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = exception;
                } else {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }
        if (cleanupFailure == null) {
            return;
        }
        if (originalFailure != null) {
            originalFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw cleanupFailure;
    }

    private void rollback(
            LinkedHashMap<String, byte[]> original,
            boolean dataDirectoryExisted,
            Throwable restoreFailure) {
        for (String name : ManagedDataFiles.FILE_NAMES) {
            Path destination = dataDirectory.resolve(name);
            try {
                byte[] content = original.get(name);
                if (content == null) {
                    Files.deleteIfExists(destination);
                } else {
                    SafeFileSupport.write(
                            destination,
                            content,
                            true,
                            ".spendwise-rollback-",
                            "rollback data");
                }
            } catch (RuntimeException | IOException rollbackFailure) {
                restoreFailure.addSuppressed(rollbackFailure);
            }
        }
        if (!dataDirectoryExisted) {
            try {
                Files.deleteIfExists(dataDirectory);
            } catch (IOException | SecurityException rollbackFailure) {
                restoreFailure.addSuppressed(rollbackFailure);
            }
        }
    }

    private record ManifestValues(Instant createdAt, List<String> files) {
    }

    private record ValidatedBackup(
            BackupInspection inspection,
            LinkedHashMap<String, byte[]> files) {
    }
}
