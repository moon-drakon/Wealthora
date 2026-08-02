package com.spendwise.repository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CsvFileSupport {

    private static final char UTF_8_BOM = '\uFEFF';

    private CsvFileSupport() {
    }

    public static Optional<String> read(Path path, String dataName) {
        try {
            if (Files.notExists(path)) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException(
                    "Could not read " + dataName + " CSV data.", exception);
        }
    }

    public static void write(
            Path path,
            String temporaryPrefix,
            String csvText,
            String dataName) {
        Objects.requireNonNull(csvText, "CSV text is required.");
        Path parentDirectory = path.getParent();
        if (parentDirectory == null) {
            throw new RepositoryException(
                    dataName + " CSV path must have a parent directory.");
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(parentDirectory);
            temporaryFile = Files.createTempFile(
                    parentDirectory, temporaryPrefix, ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(csvText);
                writer.flush();
            }
            try (FileChannel channel = FileChannel.open(
                    temporaryFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            replace(path, temporaryFile);
            temporaryFile = null;
        } catch (IOException | SecurityException exception) {
            cleanUpTemporaryFile(temporaryFile, exception);
            throw new RepositoryException(
                    "Could not save " + dataName + " CSV data.", exception);
        }
    }

    public static List<List<String>> parse(
            String csvText,
            List<String> expectedHeader,
            String dataName) {
        Objects.requireNonNull(csvText, "CSV text is required.");
        Objects.requireNonNull(expectedHeader, "CSV header fields are required.");
        if (csvText.isEmpty()) {
            return List.of();
        }

        String content = csvText.charAt(0) == UTF_8_BOM
                ? csvText.substring(1)
                : csvText;
        if (content.indexOf(UTF_8_BOM) >= 0) {
            throw new RepositoryException(
                    "A UTF-8 BOM is allowed only before the "
                    + dataName + " CSV header.");
        }

        List<List<String>> records = parseRecords(content, dataName);
        if (records.isEmpty() || !records.get(0).equals(expectedHeader)) {
            throw new RepositoryException(
                    dataName + " CSV header must be exactly: "
                    + String.join(",", expectedHeader));
        }
        return records;
    }

    public static void appendField(StringBuilder csv, String value) {
        String requiredValue = Objects.requireNonNull(
                value, "CSV field value is required.");
        boolean quote = requiredValue.indexOf(',') >= 0
                || requiredValue.indexOf('"') >= 0
                || requiredValue.indexOf('\n') >= 0
                || requiredValue.indexOf('\r') >= 0;
        if (!quote) {
            csv.append(requiredValue);
            return;
        }
        csv.append('"');
        for (int index = 0; index < requiredValue.length(); index++) {
            char character = requiredValue.charAt(index);
            if (character == '"') {
                csv.append("\"\"");
            } else {
                csv.append(character);
            }
        }
        csv.append('"');
    }

    private static List<List<String>> parseRecords(
            String content, String dataName) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean started = false;

        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            int recordNumber = records.size() + 1;
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < content.length()
                            && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (closedQuote) {
                if (character == ',') {
                    finishField(record, field);
                    closedQuote = false;
                    started = false;
                } else if (character == '\n' || character == '\r') {
                    index = finishLineEnding(
                            content,
                            index,
                            character,
                            dataName,
                            recordNumber);
                    finishRecord(records, record, field);
                    closedQuote = false;
                    started = false;
                } else {
                    throw corrupt(
                            dataName,
                            recordNumber,
                            "illegal text after a closing quote.");
                }
            } else if (character == '"') {
                if (started || field.length() > 0) {
                    throw corrupt(
                            dataName, recordNumber, "illegal quote placement.");
                }
                quoted = true;
                started = true;
            } else if (character == ',') {
                finishField(record, field);
                started = false;
            } else if (character == '\n' || character == '\r') {
                index = finishLineEnding(
                        content,
                        index,
                        character,
                        dataName,
                        recordNumber);
                finishRecord(records, record, field);
                started = false;
            } else {
                field.append(character);
                started = true;
            }
        }
        if (quoted) {
            throw corrupt(
                    dataName,
                    records.size() + 1,
                    "unclosed quoted field.");
        }
        if (closedQuote || started || field.length() > 0 || !record.isEmpty()) {
            finishRecord(records, record, field);
        }
        return records;
    }

    private static int finishLineEnding(
            String content,
            int index,
            char character,
            String dataName,
            int recordNumber) {
        if (character == '\r') {
            if (index + 1 >= content.length()
                    || content.charAt(index + 1) != '\n') {
                throw corrupt(
                        dataName, recordNumber, "unsupported record ending.");
            }
            return index + 1;
        }
        return index;
    }

    private static void finishField(
            List<String> record, StringBuilder field) {
        record.add(field.toString());
        field.setLength(0);
    }

    private static void finishRecord(
            List<List<String>> records,
            List<String> record,
            StringBuilder field) {
        finishField(record, field);
        records.add(List.copyOf(record));
        record.clear();
    }

    private static RepositoryException corrupt(
            String dataName, int recordNumber, String detail) {
        return new RepositoryException(
                dataName + " CSV record " + recordNumber
                + " is invalid: " + detail);
    }

    private static void replace(Path destination, Path temporaryFile)
            throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupportedException) {
            try {
                Files.move(
                        temporaryFile,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackException) {
                fallbackException.addSuppressed(unsupportedException);
                throw fallbackException;
            }
        }
    }

    private static void cleanUpTemporaryFile(
            Path temporaryFile, Throwable originalFailure) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException | SecurityException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
