package com.spendwise.service;

import com.spendwise.config.AppBrand;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JsonBackupService {
    public static final int FORMAT_VERSION = 1;
    private static final Pattern HEADER = Pattern.compile(
            "\\A\\{\\s*\"application\":\"(?:Wealthora|SpendWise Expense Tracker)\",\\s*"
            + "\"formatVersion\":1,\\s*\"createdAt\":\"([^\"]+)\",\\s*"
            + "\"files\":\\[(.*)]\\s*}\\s*\\z", Pattern.DOTALL);
    private static final Pattern FILE = Pattern.compile(
            "\\s*\\{\"name\":\"([a-z-]+\\.csv)\","
            + "\"sha256\":\"([0-9a-f]{64})\","
            + "\"contentBase64\":\"([A-Za-z0-9+/=]*)\"}\\s*");
    private final Path dataDirectory;
    private final BackupService zipBackupService;
    private final Clock clock;

    public JsonBackupService(Path dataDirectory) {
        this(dataDirectory, Clock.systemUTC());
    }

    JsonBackupService(Path dataDirectory, Clock clock) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory)
                .toAbsolutePath().normalize();
        this.zipBackupService = new BackupService(this.dataDirectory);
        this.clock = Objects.requireNonNull(clock);
    }

    public BackupResult createBackup(Path destination, boolean allowOverwrite) {
        Path target = validateDestination(destination);
        Instant createdAt = clock.instant();
        LinkedHashMap<String, byte[]> files = readCurrentData();
        SafeFileSupport.write(target, encode(files, createdAt), allowOverwrite,
                ".spendwise-json-backup-", "JSON backup");
        return new BackupResult(target, createdAt, List.copyOf(files.keySet()));
    }

    public BackupInspection inspectBackup(Path source) {
        ParsedBackup parsed = parse(source);
        return new BackupInspection(source.toAbsolutePath().normalize(),
                parsed.createdAt(), List.copyOf(parsed.files().keySet()));
    }

    public RestoreResult restoreBackup(Path source) {
        Path jsonSource = Objects.requireNonNull(source)
                .toAbsolutePath().normalize();
        ParsedBackup parsed = parse(jsonSource);
        Path parent = jsonSource.getParent();
        if (parent == null) {
            throw new ValidationException(
                    "JSON backup must have a parent directory.");
        }
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(
                    parent, ".spendwise-json-restore-", ".zip");
            Files.write(temporary, zipArchive(parsed.files(), parsed.createdAt()));
            return zipBackupService.restoreBackup(temporary);
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException(
                    "Could not prepare JSON backup restore.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException exception) {
                    throw new RepositoryException(
                            "Could not remove temporary JSON restore data.", exception);
                }
            }
        }
    }

    private Path validateDestination(Path destination) {
        Path target = Objects.requireNonNull(destination)
                .toAbsolutePath().normalize();
        if (target.startsWith(dataDirectory)) {
            throw new ValidationException(
                    "Backups must be stored outside the application data directory.");
        }
        return target;
    }

    private LinkedHashMap<String, byte[]> readCurrentData() {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        for (String name : ManagedDataFiles.FILE_NAMES) {
            Path file = dataDirectory.resolve(name);
            try {
                if (Files.notExists(file)) continue;
                if (!Files.isRegularFile(file)) {
                    throw new RepositoryException(
                            "Managed data path is not a file: " + name);
                }
                result.put(name, Files.readAllBytes(file));
            } catch (IOException | SecurityException exception) {
                throw new RepositoryException(
                        "Could not read managed data file " + name + ".",
                        exception);
            }
        }
        return result;
    }

    private static byte[] encode(
            LinkedHashMap<String, byte[]> files, Instant createdAt) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"application\":\"")
                .append(AppBrand.APP_NAME).append("\",\n")
                .append("  \"formatVersion\":1,\n")
                .append("  \"createdAt\":\"").append(createdAt)
                .append("\",\n  \"files\":[\n");
        int index = 0;
        for (var entry : files.entrySet()) {
            if (index++ > 0) json.append(",\n");
            json.append("    {\"name\":\"").append(entry.getKey())
                    .append("\",\"sha256\":\"").append(sha256(entry.getValue()))
                    .append("\",\"contentBase64\":\"")
                    .append(Base64.getEncoder().encodeToString(entry.getValue()))
                    .append("\"}");
        }
        json.append("\n  ]\n}\n");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static ParsedBackup parse(Path source) {
        if (!Files.isRegularFile(source)) {
            throw new ValidationException("Select an existing JSON backup file.");
        }
        String text;
        try {
            if (Files.size(source) > 150L * 1024L * 1024L) {
                throw new ValidationException("JSON backup is too large.");
            }
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException("Could not read JSON backup.", exception);
        }
        Matcher header = HEADER.matcher(text);
        if (!header.matches()) {
            throw new ValidationException(
                    "JSON backup format or version is unsupported.");
        }
        Instant createdAt;
        try { createdAt = Instant.parse(header.group(1)); }
        catch (RuntimeException exception) {
            throw new ValidationException("JSON backup timestamp is invalid.");
        }
        String filesText = header.group(2);
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        Matcher matcher = FILE.matcher(filesText);
        int cursor = 0;
        while (matcher.find()) {
            String separator = filesText.substring(cursor, matcher.start()).strip();
            if (!separator.isEmpty() && !separator.equals(",")) {
                throw new ValidationException("JSON backup file list is malformed.");
            }
            String name = matcher.group(1);
            if (!ManagedDataFiles.FILE_NAMES.contains(name)
                    || files.containsKey(name)) {
                throw new ValidationException(
                        "JSON backup contains an unsupported filename.");
            }
            byte[] content;
            try { content = Base64.getDecoder().decode(matcher.group(3)); }
            catch (IllegalArgumentException exception) {
                throw new ValidationException("JSON backup content is corrupted.");
            }
            if (!sha256(content).equals(matcher.group(2))) {
                throw new ValidationException("JSON backup checksum does not match.");
            }
            files.put(name, content);
            cursor = matcher.end();
        }
        if (!filesText.substring(cursor).strip().isEmpty()) {
            throw new ValidationException("JSON backup file list is malformed.");
        }
        List<String> expectedOrder = ManagedDataFiles.FILE_NAMES.stream()
                .filter(files::containsKey).toList();
        if (!expectedOrder.equals(new ArrayList<>(files.keySet()))) {
            throw new ValidationException(
                    "JSON backup filenames are not in the supported order.");
        }
        return new ParsedBackup(createdAt, files);
    }

    private static byte[] zipArchive(
            LinkedHashMap<String, byte[]> files, Instant createdAt) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            String manifest = "formatVersion=" + BackupService.FORMAT_VERSION
                    + "\napplication=" + BackupService.APPLICATION_NAME
                    + "\ncreatedAt=" + createdAt
                    + "\nfiles=" + String.join(";", files.keySet()) + "\n";
            put(zip, BackupService.MANIFEST_NAME,
                    manifest.getBytes(StandardCharsets.UTF_8));
            for (var entry : files.entrySet()) {
                put(zip, entry.getKey(), entry.getValue());
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new RepositoryException("Could not prepare restore archive.", exception);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content)
            throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0L);
        zip.putNextEntry(entry); zip.write(content); zip.closeEntry();
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record ParsedBackup(
            Instant createdAt, LinkedHashMap<String, byte[]> files) {
    }
}
