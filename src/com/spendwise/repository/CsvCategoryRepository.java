package com.spendwise.repository;

import com.spendwise.model.Category;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class CsvCategoryRepository implements CategoryRepository {

    public static final String HEADER = "id,name,status";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ARCHIVED_STATUS = "ARCHIVED";
    private static final char UTF_8_BOM = '\uFEFF';
    private static final int COLUMN_COUNT = 3;

    private final Path csvPath;
    private final boolean attemptAtomicMove;

    public CsvCategoryRepository(Path csvPath) {
        this(csvPath, true);
    }

    CsvCategoryRepository(Path csvPath, boolean attemptAtomicMove) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Category CSV path is required.")
                .toAbsolutePath()
                .normalize();
        this.attemptAtomicMove = attemptAtomicMove;
    }

    @Override
    public List<Category> findAll() {
        try {
            if (Files.notExists(csvPath)) {
                return List.of();
            }
            String csvText = Files.readString(csvPath, StandardCharsets.UTF_8);
            return decode(csvText);
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException("Could not read category CSV data.", exception);
        }
    }

    @Override
    public void add(Category category) {
        Category requiredCategory = requireCustomCategory(category);
        List<Category> categories = new ArrayList<>(findAll());
        if (indexOfIdentifier(categories, requiredCategory.getIdentifier()) >= 0) {
            throw new RepositoryException(
                    "Category identifier already exists: "
                    + requiredCategory.getIdentifier());
        }
        rejectDuplicateName(categories, requiredCategory, null);
        categories.add(requiredCategory);
        writeSnapshot(categories);
    }

    @Override
    public void update(Category category) {
        Category requiredCategory = requireCustomCategory(category);
        List<Category> categories = new ArrayList<>(findAll());
        int existingIndex = indexOfIdentifier(
                categories, requiredCategory.getIdentifier());
        if (existingIndex < 0) {
            throw new RepositoryException(
                    "Category identifier does not exist: "
                    + requiredCategory.getIdentifier());
        }
        rejectDuplicateName(
                categories, requiredCategory, requiredCategory.getIdentifier());
        categories.set(existingIndex, requiredCategory);
        writeSnapshot(categories);
    }

    private void writeSnapshot(List<Category> categories) {
        String csvText = encode(categories);
        Path parentDirectory = csvPath.getParent();
        if (parentDirectory == null) {
            throw new RepositoryException(
                    "Category CSV path must have a parent directory.");
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(parentDirectory);
            temporaryFile = Files.createTempFile(
                    parentDirectory, ".spendwise-categories-", ".tmp");
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
            replaceWithTemporaryFile(temporaryFile);
            temporaryFile = null;
        } catch (IOException | SecurityException exception) {
            cleanUpTemporaryFile(temporaryFile, exception);
            throw new RepositoryException("Could not save category CSV data.", exception);
        }
    }

    private void replaceWithTemporaryFile(Path temporaryFile) throws IOException {
        if (attemptAtomicMove) {
            try {
                Files.move(
                        temporaryFile,
                        csvPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException unsupportedException) {
                try {
                    moveWithReplacement(temporaryFile);
                    return;
                } catch (IOException fallbackException) {
                    fallbackException.addSuppressed(unsupportedException);
                    throw fallbackException;
                }
            }
        }
        moveWithReplacement(temporaryFile);
    }

    private void moveWithReplacement(Path temporaryFile) throws IOException {
        Files.move(
                temporaryFile,
                csvPath,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String encode(List<Category> categories) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        Set<String> identifiers = new HashSet<>();
        Set<String> normalizedNames = new HashSet<>();
        for (Category category : categories) {
            Category requiredCategory = requireCustomCategory(category);
            if (!identifiers.add(requiredCategory.getIdentifier())) {
                throw new RepositoryException(
                        "Category snapshot contains a duplicate identifier: "
                        + requiredCategory.getIdentifier());
            }
            if (!normalizedNames.add(normalizedName(requiredCategory))) {
                throw new RepositoryException(
                        "Category snapshot contains a duplicate name: "
                        + requiredCategory.getDisplayName());
            }
            appendField(csv, requiredCategory.getIdentifier());
            csv.append(',');
            appendField(csv, requiredCategory.getDisplayName());
            csv.append(',');
            appendField(csv, requiredCategory.isArchived()
                    ? ARCHIVED_STATUS
                    : ACTIVE_STATUS);
            csv.append('\n');
        }
        return csv.toString();
    }

    private static List<Category> decode(String csvText) {
        if (csvText == null) {
            throw new RepositoryException("Category CSV text is required.");
        }
        if (csvText.isEmpty()) {
            throw new RepositoryException("Category CSV header is missing.");
        }
        String content = csvText.charAt(0) == UTF_8_BOM
                ? csvText.substring(1)
                : csvText;
        if (content.indexOf(UTF_8_BOM) >= 0) {
            throw new RepositoryException(
                    "A UTF-8 BOM is allowed only before the category CSV header.");
        }

        List<List<String>> records = parseRecords(content);
        if (records.isEmpty()
                || !records.get(0).equals(List.of("id", "name", "status"))) {
            throw new RepositoryException(
                    "Category CSV header must be exactly: " + HEADER);
        }

        List<Category> categories = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        Set<String> normalizedNames = builtInNames();
        for (int index = 1; index < records.size(); index++) {
            int recordNumber = index + 1;
            List<String> fields = records.get(index);
            if (fields.size() != COLUMN_COUNT) {
                throw corrupt(
                        recordNumber, "expected exactly three columns.");
            }
            boolean archived = switch (fields.get(2)) {
                case ACTIVE_STATUS -> false;
                case ARCHIVED_STATUS -> true;
                default -> throw corrupt(
                        recordNumber, "status must be ACTIVE or ARCHIVED.");
            };

            Category category;
            try {
                category = Category.createCustom(
                        fields.get(0), fields.get(1), archived);
            } catch (IllegalArgumentException exception) {
                throw new RepositoryException(
                        "Category CSV record " + recordNumber
                        + " contains invalid category data: "
                        + exception.getMessage(),
                        exception);
            }
            if (!identifiers.add(category.getIdentifier())) {
                throw corrupt(recordNumber, "duplicate category identifier.");
            }
            if (!normalizedNames.add(normalizedName(category))) {
                throw corrupt(recordNumber, "duplicate category name.");
            }
            categories.add(category);
        }
        return List.copyOf(categories);
    }

    private static List<List<String>> parseRecords(String content) {
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
                            content, index, character, recordNumber);
                    finishRecord(records, record, field);
                    closedQuote = false;
                    started = false;
                } else {
                    throw corrupt(recordNumber, "illegal text after a closing quote.");
                }
            } else if (character == '"') {
                if (started || field.length() > 0) {
                    throw corrupt(recordNumber, "illegal quote placement.");
                }
                quoted = true;
                started = true;
            } else if (character == ',') {
                finishField(record, field);
                started = false;
            } else if (character == '\n' || character == '\r') {
                index = finishLineEnding(content, index, character, recordNumber);
                finishRecord(records, record, field);
                started = false;
            } else {
                field.append(character);
                started = true;
            }
        }
        if (quoted) {
            throw corrupt(records.size() + 1, "unclosed quoted field.");
        }
        if (closedQuote || started || field.length() > 0 || !record.isEmpty()) {
            finishRecord(records, record, field);
        }
        return records;
    }

    private static int finishLineEnding(
            String content, int index, char character, int recordNumber) {
        if (character == '\r') {
            if (index + 1 >= content.length() || content.charAt(index + 1) != '\n') {
                throw corrupt(recordNumber, "unsupported record ending.");
            }
            return index + 1;
        }
        return index;
    }

    private static void finishField(List<String> record, StringBuilder field) {
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

    private static void appendField(StringBuilder csv, String value) {
        boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!quote) {
            csv.append(value);
            return;
        }
        csv.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            csv.append(character == '"' ? "\"\"" : character);
        }
        csv.append('"');
    }

    private static void rejectDuplicateName(
            List<Category> categories,
            Category candidate,
            String ignoredIdentifier) {
        String candidateName = normalizedName(candidate);
        for (Category category : Category.values()) {
            if (normalizedName(category).equals(candidateName)) {
                throw new RepositoryException(
                        "Category name already exists: "
                        + candidate.getDisplayName());
            }
        }
        for (Category category : categories) {
            if (!category.getIdentifier().equals(ignoredIdentifier)
                    && normalizedName(category).equals(candidateName)) {
                throw new RepositoryException(
                        "Category name already exists: "
                        + candidate.getDisplayName());
            }
        }
    }

    private static Set<String> builtInNames() {
        Set<String> names = new HashSet<>();
        for (Category category : Category.values()) {
            names.add(normalizedName(category));
        }
        return names;
    }

    private static String normalizedName(Category category) {
        return category.getDisplayName().toLowerCase(Locale.ROOT);
    }

    private static Category requireCustomCategory(Category category) {
        if (category == null) {
            throw new RepositoryException("Custom category is required.");
        }
        if (category.isBuiltIn()) {
            throw new RepositoryException(
                    "Built-in categories are not stored in categories.csv.");
        }
        return category;
    }

    private static int indexOfIdentifier(
            List<Category> categories, String identifier) {
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).getIdentifier().equals(identifier)) {
                return index;
            }
        }
        return -1;
    }

    private static RepositoryException corrupt(int recordNumber, String detail) {
        return new RepositoryException(
                "Category CSV record " + recordNumber + " is invalid: " + detail);
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
