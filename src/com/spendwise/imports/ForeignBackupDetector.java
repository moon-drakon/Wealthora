package com.spendwise.imports;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Works out what a file the user picked really is, by reading its contents.
 *
 * <p>Extensions are ignored on purpose. Money Manager writes {@code .mmbak}
 * files that are databases, but the same database can arrive named
 * {@code .sqlite}, {@code .db}, or inside a ZIP, and an unrelated file can
 * arrive named {@code .mmbak}. Reading the bytes is the only reliable answer.
 */
public final class ForeignBackupDetector {

    /** Tables a Money Manager backup must have before it is treated as one. */
    static final Set<String> MONEY_MANAGER_TABLES =
            Set.of("INOUTCOME", "ASSETS", "ZCATEGORY");

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final long MAX_ZIP_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_ZIP_TOTAL_BYTES = 512L * 1024L * 1024L;

    private ForeignBackupDetector() {
    }

    /** Identifies {@code file} without modifying it. */
    public static ForeignBackupFormat detect(Path file) {
        Path path = Objects.requireNonNull(file, "Import file is required.")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new SqliteFormatException("Select an existing backup file.");
        }
        byte[] header = readHeader(path);
        if (SqliteFile.hasSqliteSignature(header)) {
            return describeDatabase(() -> SqliteFile.open(path));
        }
        if (startsWith(header, ZIP_MAGIC)) {
            return describeZip(path);
        }
        if (looksLikeDelimitedText(header)) {
            return new ForeignBackupFormat(
                    ForeignBackupFormat.Kind.DELIMITED_TEXT,
                    "A spreadsheet-style export.",
                    List.of("Use Import Transactions instead for this file."));
        }
        return new ForeignBackupFormat(
                ForeignBackupFormat.Kind.UNSUPPORTED,
                "This file is not a backup " + "Wealthora" + " can read.",
                List.of());
    }

    /**
     * Opens the Money Manager database inside {@code file}, whether the file is
     * the database itself or a ZIP that contains one.
     */
    public static SqliteFile openMoneyManagerDatabase(Path file) {
        Path path = Objects.requireNonNull(file, "Import file is required.")
                .toAbsolutePath().normalize();
        byte[] header = readHeader(path);
        if (SqliteFile.hasSqliteSignature(header)) {
            return SqliteFile.open(path);
        }
        if (startsWith(header, ZIP_MAGIC)) {
            byte[] database = findDatabaseInZip(path);
            if (database != null) {
                return SqliteFile.open(database);
            }
        }
        throw new SqliteFormatException(
                "The selected file does not contain Money Manager data.");
    }

    private static ForeignBackupFormat describeDatabase(
            java.util.function.Supplier<SqliteFile> opener) {
        SqliteFile database = opener.get();
        Set<String> tables = database.tableNames();
        if (tables.containsAll(MONEY_MANAGER_TABLES)) {
            List<String> notes = new ArrayList<>();
            notes.add("Transactions: " + database.rows("INOUTCOME").size());
            notes.add("Accounts: " + database.rows("ASSETS").size());
            notes.add("Categories: " + database.rows("ZCATEGORY").size());
            return new ForeignBackupFormat(
                    ForeignBackupFormat.Kind.MONEY_MANAGER_DATABASE,
                    "A Money Manager backup.", notes);
        }
        return new ForeignBackupFormat(
                ForeignBackupFormat.Kind.UNKNOWN_DATABASE,
                "A database backup from another app.",
                List.of("Wealthora can read Money Manager backups. This file "
                        + "does not contain Money Manager data."));
    }

    private static ForeignBackupFormat describeZip(Path path) {
        List<String> entries = new ArrayList<>();
        byte[] database = null;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries.size() < 200) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries.add(entry.getName());
                byte[] content = readLimited(zip);
                total += content.length;
                if (total > MAX_ZIP_TOTAL_BYTES) {
                    throw new SqliteFormatException(
                            "The selected archive is too large to read.");
                }
                if (database == null
                        && SqliteFile.hasSqliteSignature(content)) {
                    database = content;
                }
            }
        } catch (IOException | SecurityException exception) {
            throw new SqliteFormatException(
                    "The selected archive could not be read.", exception);
        }
        if (database != null) {
            byte[] found = database;
            return describeDatabase(() -> SqliteFile.open(found));
        }
        return new ForeignBackupFormat(
                ForeignBackupFormat.Kind.ZIP_ARCHIVE,
                "An archive with no recognised finance database.",
                entries.isEmpty() ? List.of() : List.of(
                        "Contents: " + String.join(", ",
                                entries.subList(0, Math.min(10, entries.size())))));
    }

    private static byte[] findDatabaseInZip(Path path) {
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] content = readLimited(zip);
                total += content.length;
                if (total > MAX_ZIP_TOTAL_BYTES) {
                    throw new SqliteFormatException(
                            "The selected archive is too large to read.");
                }
                if (SqliteFile.hasSqliteSignature(content)) {
                    return content;
                }
            }
        } catch (IOException | SecurityException exception) {
            throw new SqliteFormatException(
                    "The selected archive could not be read.", exception);
        }
        return null;
    }

    /**
     * Reads one archive entry with a hard ceiling, so a small archive that
     * claims to expand without limit cannot exhaust memory.
     */
    private static byte[] readLimited(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            count += read;
            if (count > MAX_ZIP_ENTRY_BYTES) {
                throw new SqliteFormatException(
                        "The selected archive contains an oversized file.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static byte[] readHeader(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] header = new byte[64];
            int read = stream.readNBytes(header, 0, header.length);
            if (read < header.length) {
                byte[] shortened = new byte[Math.max(read, 0)];
                System.arraycopy(header, 0, shortened, 0, shortened.length);
                return shortened;
            }
            return header;
        } catch (IOException | SecurityException exception) {
            throw new SqliteFormatException(
                    "The selected file could not be read.", exception);
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeDelimitedText(byte[] header) {
        if (header.length == 0) {
            return false;
        }
        String text = new String(header, StandardCharsets.UTF_8);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isISOControl(character) && character != '\n'
                    && character != '\r' && character != '\t') {
                return false;
            }
        }
        return text.indexOf(',') >= 0 || text.indexOf('\t') >= 0
                || text.indexOf(';') >= 0;
    }
}
