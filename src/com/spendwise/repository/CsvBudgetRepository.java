package com.spendwise.repository;

import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public class CsvBudgetRepository implements BudgetRepository {

    static final String HEADER = "month,scope,category,amount";
    private static final String OVERALL_SCOPE = "OVERALL";
    private static final String CATEGORY_SCOPE = "CATEGORY";

    private final Path csvPath;
    private final Function<String, Category> categoryResolver;

    public CsvBudgetRepository(Path csvPath) {
        this(csvPath, Category::valueOf);
    }

    public CsvBudgetRepository(
            Path csvPath, Function<String, Category> categoryResolver) {
        this.csvPath = Objects.requireNonNull(csvPath, "Budget CSV path is required.")
                .toAbsolutePath()
                .normalize();
        this.categoryResolver = Objects.requireNonNull(
                categoryResolver, "Category resolver is required.");
    }

    @Override
    public Optional<MonthlyBudget> findByMonth(YearMonth month) {
        YearMonth requiredMonth = Objects.requireNonNull(
                month, "Budget month is required.");
        return Optional.ofNullable(readAll().get(requiredMonth));
    }

    @Override
    public void save(MonthlyBudget budget) {
        MonthlyBudget requiredBudget = Objects.requireNonNull(
                budget, "Monthly budget is required.");
        TreeMap<YearMonth, MonthlyBudget> budgets = readAll();
        budgets.put(requiredBudget.getMonth(), requiredBudget);
        writeSnapshot(budgets);
    }

    @Override
    public boolean delete(YearMonth month) {
        YearMonth requiredMonth = Objects.requireNonNull(
                month, "Budget month is required.");
        TreeMap<YearMonth, MonthlyBudget> budgets = readAll();
        if (budgets.remove(requiredMonth) == null) {
            return false;
        }
        writeSnapshot(budgets);
        return true;
    }

    @Override
    public boolean isCategoryReferenced(Category category) {
        Category requiredCategory = Objects.requireNonNull(
                category, "Budget category is required.");
        return readAll().values().stream()
                .anyMatch(budget ->
                    budget.getCategoryLimits().containsKey(requiredCategory));
    }

    private TreeMap<YearMonth, MonthlyBudget> readAll() {
        try {
            if (Files.notExists(csvPath)) {
                return new TreeMap<>();
            }
            String csvText = Files.readString(csvPath, StandardCharsets.UTF_8);
            return decode(csvText, categoryResolver);
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException("Could not read budget CSV data.", exception);
        }
    }

    private static TreeMap<YearMonth, MonthlyBudget> decode(
            String csvText, Function<String, Category> categoryResolver) {
        TreeMap<YearMonth, MonthlyBudget> budgets = new TreeMap<>();
        if (csvText.isEmpty()) {
            throw new RepositoryException(
                    "Budget CSV header is missing.");
        }
        if (csvText.indexOf('\uFEFF', 1) >= 0) {
            throw new RepositoryException(
                    "Budget CSV contains an invalid byte-order mark.");
        }

        String normalizedText = csvText.charAt(0) == '\uFEFF'
                ? csvText.substring(1)
                : csvText;
        String[] lines = normalizedText.split("\\r\\n|\\n|\\r", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw new RepositoryException(
                    "Budget CSV header must be exactly: " + HEADER);
        }

        TreeMap<YearMonth, BudgetBuilder> builders = new TreeMap<>();
        int lastLine = lines.length;
        if (lastLine > 1 && lines[lastLine - 1].isEmpty()) {
            lastLine--;
        }
        for (int index = 1; index < lastLine; index++) {
            int recordNumber = index + 1;
            String line = lines[index];
            if (line.isEmpty()) {
                throw corrupt(recordNumber, "record is blank.");
            }
            if (line.indexOf('"') >= 0) {
                throw corrupt(recordNumber, "quoted fields are not valid in budget data.");
            }
            String[] fields = line.split(",", -1);
            if (fields.length != 4) {
                throw corrupt(recordNumber, "expected exactly four columns.");
            }

            YearMonth month = parseMonth(fields[0], recordNumber);
            BigDecimal amount = parseAmount(fields[3], recordNumber);
            BudgetBuilder builder = builders.computeIfAbsent(
                    month, ignored -> new BudgetBuilder(categoryResolver));
            switch (fields[1]) {
                case OVERALL_SCOPE ->
                    builder.setOverall(fields[2], amount, recordNumber);
                case CATEGORY_SCOPE ->
                    builder.setCategory(fields[2], amount, recordNumber);
                default -> throw corrupt(
                        recordNumber, "scope must be OVERALL or CATEGORY.");
            }
        }

        for (Map.Entry<YearMonth, BudgetBuilder> entry : builders.entrySet()) {
            budgets.put(entry.getKey(), entry.getValue().build(entry.getKey()));
        }
        return budgets;
    }

    private static YearMonth parseMonth(String text, int recordNumber) {
        try {
            if (!text.matches("\\d{4,}-\\d{2}")) {
                throw new DateTimeParseException(
                        "Budget month must use yyyy-MM.", text, 0);
            }
            return YearMonth.parse(text);
        } catch (DateTimeParseException exception) {
            throw new RepositoryException(
                    "Budget CSV record " + recordNumber
                    + " has an invalid month.",
                    exception);
        }
    }

    private static BigDecimal parseAmount(String text, int recordNumber) {
        if (!text.matches("\\d+\\.\\d{2}")) {
            throw corrupt(
                    recordNumber,
                    "amount must be a positive number with exactly two decimal places.");
        }
        try {
            BigDecimal amount = new BigDecimal(text);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw corrupt(recordNumber, "amount must be greater than zero.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new RepositoryException(
                    "Budget CSV record " + recordNumber
                    + " has an invalid amount.",
                    exception);
        }
    }

    private static RepositoryException corrupt(int recordNumber, String detail) {
        return new RepositoryException(
                "Budget CSV record " + recordNumber + " is invalid: " + detail);
    }

    private void writeSnapshot(Map<YearMonth, MonthlyBudget> budgets) {
        String csvText = encode(budgets);
        Path parentDirectory = csvPath.getParent();
        if (parentDirectory == null) {
            throw new RepositoryException(
                    "Budget CSV path must have a parent directory.");
        }
        Path temporaryFile = null;
        try {
            Files.createDirectories(parentDirectory);
            temporaryFile = Files.createTempFile(
                    parentDirectory, ".spendwise-budgets-", ".tmp");
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
            throw new RepositoryException("Could not save budget CSV data.", exception);
        }
    }

    private static String encode(Map<YearMonth, MonthlyBudget> budgets) {
        StringBuilder csvText = new StringBuilder(HEADER).append('\n');
        for (MonthlyBudget budget : new TreeMap<>(budgets).values()) {
            budget.getOverallLimit().ifPresent(limit ->
                    appendRow(
                            csvText,
                            budget.getMonth(),
                            OVERALL_SCOPE,
                            "",
                            limit));
            for (Map.Entry<Category, BigDecimal> entry
                    : budget.getCategoryLimits().entrySet()) {
                Category category = entry.getKey();
                BigDecimal limit = entry.getValue();
                appendRow(
                        csvText,
                        budget.getMonth(),
                        CATEGORY_SCOPE,
                        category.name(),
                        limit);
            }
        }
        return csvText.toString();
    }

    private static void appendRow(
            StringBuilder csvText,
            YearMonth month,
            String scope,
            String category,
            BigDecimal amount) {
        csvText.append(month)
                .append(',')
                .append(scope)
                .append(',')
                .append(category)
                .append(',')
                .append(amount.toPlainString())
                .append('\n');
    }

    void replaceWithTemporaryFile(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    csvPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupportedException) {
            try {
                Files.move(
                        temporaryFile,
                        csvPath,
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

    private static final class BudgetBuilder {

        private BigDecimal overallLimit;
        private final Function<String, Category> categoryResolver;
        private final Map<Category, BigDecimal> categoryLimits =
                new LinkedHashMap<>();

        BudgetBuilder(Function<String, Category> categoryResolver) {
            this.categoryResolver = Objects.requireNonNull(
                    categoryResolver, "Category resolver is required.");
        }

        void setOverall(
                String categoryText, BigDecimal amount, int recordNumber) {
            if (!categoryText.isEmpty()) {
                throw corrupt(
                        recordNumber,
                        "OVERALL scope requires an empty category.");
            }
            if (overallLimit != null) {
                throw corrupt(
                        recordNumber,
                        "duplicate OVERALL limit for the month.");
            }
            overallLimit = amount;
        }

        void setCategory(
                String categoryText, BigDecimal amount, int recordNumber) {
            Category category;
            try {
                category = categoryResolver.apply(categoryText);
            } catch (RuntimeException exception) {
                throw new RepositoryException(
                        "Budget CSV record " + recordNumber
                        + " has an unknown category.",
                        exception);
            }
            if (categoryLimits.putIfAbsent(category, amount) != null) {
                throw corrupt(
                        recordNumber,
                        "duplicate " + category.name() + " limit for the month.");
            }
        }

        MonthlyBudget build(YearMonth month) {
            return new MonthlyBudget(
                    month,
                    Optional.ofNullable(overallLimit),
                    categoryLimits);
        }
    }
}
