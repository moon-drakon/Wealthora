package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CsvRecurringEntryRepository
        implements RecurringEntryRepository {

    public static final String HEADER = "id,type,amount,description,category,"
            + "sourceAccount,destinationAccount,frequency,interval,"
            + "startDate,endDate,nextDueDate,status";
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "type", "amount", "description", "category",
            "sourceAccount", "destinationAccount", "frequency", "interval",
            "startDate", "endDate", "nextDueDate", "status");
    private static final int COLUMN_COUNT = 13;

    private final Path csvPath;
    private final Function<String, Category> categoryResolver;
    private final Function<String, Account> accountResolver;

    public CsvRecurringEntryRepository(
            Path csvPath,
            Function<String, Category> categoryResolver,
            Function<String, Account> accountResolver) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Recurring CSV path is required.")
                .toAbsolutePath()
                .normalize();
        this.categoryResolver = Objects.requireNonNull(
                categoryResolver, "Category resolver is required.");
        this.accountResolver = Objects.requireNonNull(
                accountResolver, "Account resolver is required.");
    }

    @Override
    public List<RecurringEntry> findAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "recurring entry");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return List.of();
        }
        return decode(content.orElseThrow());
    }

    @Override
    public Optional<RecurringEntry> findById(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(entry -> entry.getIdentifier().equals(identifier))
                .findFirst();
    }

    @Override
    public void add(RecurringEntry entry) {
        RecurringEntry requiredEntry = Objects.requireNonNull(
                entry, "Recurring entry is required.");
        List<RecurringEntry> entries = new ArrayList<>(findAll());
        if (indexOf(entries, requiredEntry.getIdentifier()) >= 0) {
            throw new RepositoryException(
                    "Recurring entry ID already exists: "
                    + requiredEntry.getIdentifier());
        }
        entries.add(requiredEntry);
        write(entries);
    }

    @Override
    public void update(RecurringEntry entry) {
        RecurringEntry requiredEntry = Objects.requireNonNull(
                entry, "Recurring entry is required.");
        List<RecurringEntry> entries = new ArrayList<>(findAll());
        int index = indexOf(entries, requiredEntry.getIdentifier());
        if (index < 0) {
            throw new RepositoryException(
                    "Recurring entry ID does not exist: "
                    + requiredEntry.getIdentifier());
        }
        entries.set(index, requiredEntry);
        write(entries);
    }

    private List<RecurringEntry> decode(String content) {
        List<List<String>> records = CsvFileSupport.parse(
                content, HEADER_FIELDS, "Recurring entry");
        List<RecurringEntry> entries = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            int recordNumber = index + 1;
            List<String> fields = records.get(index);
            if (fields.size() != COLUMN_COUNT) {
                throw corrupt(
                        recordNumber,
                        "must contain exactly " + COLUMN_COUNT + " columns.",
                        null);
            }
            try {
                RecurringEntryType type = RecurringEntryType.valueOf(fields.get(1));
                Category category = fields.get(4).isEmpty()
                        ? null
                        : categoryResolver.apply(fields.get(4));
                Account destination = fields.get(6).isEmpty()
                        ? null
                        : accountResolver.apply(fields.get(6));
                LocalDate endDate = fields.get(10).isEmpty()
                        ? null
                        : LocalDate.parse(fields.get(10));
                RecurringEntry entry = new RecurringEntry(
                        fields.get(0),
                        type,
                        new BigDecimal(fields.get(2)),
                        fields.get(3),
                        category,
                        accountResolver.apply(fields.get(5)),
                        destination,
                        RecurrenceFrequency.valueOf(fields.get(7)),
                        Integer.parseInt(fields.get(8)),
                        LocalDate.parse(fields.get(9)),
                        endDate,
                        LocalDate.parse(fields.get(11)),
                        parseStatus(fields.get(12)));
                if (!identifiers.add(entry.getIdentifier())) {
                    throw corrupt(
                            recordNumber,
                            "contains duplicate recurring entry ID "
                            + entry.getIdentifier() + ".",
                            null);
                }
                entries.add(entry);
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw corrupt(
                        recordNumber,
                        "contains invalid data: " + safeMessage(exception),
                        exception);
            }
        }
        return List.copyOf(entries);
    }

    private void write(List<RecurringEntry> entries) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (RecurringEntry entry : entries) {
            append(csv, entry.getIdentifier());
            append(csv, entry.getType().name());
            append(csv, entry.getAmount().toPlainString());
            append(csv, entry.getDescription());
            append(csv, entry.getCategory()
                    .map(Category::getIdentifier).orElse(""));
            append(csv, entry.getSourceAccount().getIdentifier());
            append(csv, entry.getDestinationAccount()
                    .map(Account::getIdentifier).orElse(""));
            append(csv, entry.getFrequency().name());
            append(csv, Integer.toString(entry.getInterval()));
            append(csv, entry.getStartDate().toString());
            append(csv, entry.getEndDate().map(LocalDate::toString).orElse(""));
            append(csv, entry.getNextDueDate().toString());
            CsvFileSupport.appendField(
                    csv, entry.isActive() ? "ACTIVE" : "INACTIVE");
            csv.append('\n');
        }
        CsvFileSupport.write(
                csvPath,
                ".spendwise-recurring-",
                csv.toString(),
                "recurring entry");
    }

    private static void append(StringBuilder csv, String value) {
        CsvFileSupport.appendField(csv, value);
        csv.append(',');
    }

    private static boolean parseStatus(String value) {
        return switch (value) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> throw new IllegalArgumentException(
                    "Recurring status must be ACTIVE or INACTIVE.");
        };
    }

    private static int indexOf(
            List<RecurringEntry> entries, String identifier) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).getIdentifier().equals(identifier)) {
                return index;
            }
        }
        return -1;
    }

    private static RepositoryException corrupt(
            int recordNumber, String detail, RuntimeException cause) {
        String message = "Recurring entry CSV record " + recordNumber
                + " " + detail;
        return cause == null
                ? new RepositoryException(message)
                : new RepositoryException(message, cause);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "invalid value"
                : message;
    }
}
