package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CsvImportService {
    private static final List<String> EXPENSE_HEADER = List.of(
            "id", "date", "description", "amount", "categoryId",
            "categoryName", "accountId", "accountName", "notes");
    private static final List<String> INCOME_HEADER = List.of(
            "id", "date", "amount", "source", "accountId", "accountName",
            "note");
    private static final List<String> TRANSFER_HEADER = List.of(
            "id", "date", "amount", "sourceAccountId", "sourceAccountName",
            "destinationAccountId", "destinationAccountName", "note");
    private static final DateTimeFormatter SAFETY = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path dataDirectory;
    private final BackupService backupService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public CsvImportService(
            Path dataDirectory, BackupService backupService,
            ExpenseService expenseService, IncomeService incomeService,
            TransferService transferService, AccountService accountService,
            CategoryService categoryService) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory)
                .toAbsolutePath().normalize();
        this.backupService = Objects.requireNonNull(backupService);
        this.expenseService = Objects.requireNonNull(expenseService);
        this.incomeService = Objects.requireNonNull(incomeService);
        this.transferService = Objects.requireNonNull(transferService);
        this.accountService = Objects.requireNonNull(accountService);
        this.categoryService = Objects.requireNonNull(categoryService);
    }

    public ImportResult importFile(Path source) {
        Path input = Objects.requireNonNull(source).toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) {
            throw new ValidationException("Select an existing CSV export file.");
        }
        if (input.startsWith(dataDirectory)) {
            throw new ValidationException(
                    "Import files must be outside the application data directory.");
        }
        List<List<String>> records = readCsv(input);
        if (records.isEmpty()) throw new ValidationException("CSV file is empty.");
        ImportBatch batch = prepare(records);
        Path safety = nextSafetyPath(input.getParent());
        backupService.createBackup(safety, false);
        try {
            batch.apply();
        } catch (RuntimeException failure) {
            try { backupService.restoreBackup(safety); }
            catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return new ImportResult(batch.type(), batch.size(), safety);
    }

    private ImportBatch prepare(List<List<String>> records) {
        List<String> header = records.get(0);
        if (header.equals(EXPENSE_HEADER)) return prepareExpenses(records);
        if (header.equals(INCOME_HEADER)) return prepareIncome(records);
        if (header.equals(TRANSFER_HEADER)) return prepareTransfers(records);
        throw new ValidationException(
                "CSV header is not a supported SpendWise export format.");
    }

    private ImportBatch prepareExpenses(List<List<String>> records) {
        List<Expense> items = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = requireColumns(records.get(index), 9, index + 1);
            String id = uniqueId(row.get(0), ids, index + 1);
            if (expenseService.findExpenseById(id).isPresent()) {
                throw duplicate(id);
            }
            Category category = categoryService.resolveCategory(row.get(4));
            Account account = accountService.resolveAccount(row.get(6));
            items.add(new Expense(id, row.get(2), amount(row.get(3), index + 1),
                    date(row.get(1), index + 1), category, account, row.get(8)));
        }
        return new ImportBatch("expenses", items.size(), () -> items.forEach(item ->
                expenseService.createExpenseWithId(item.getId(),
                        item.getDescription(), item.getAmount(), item.getDate(),
                        item.getCategory(), item.getAccount(), item.getNotes())));
    }

    private ImportBatch prepareIncome(List<List<String>> records) {
        List<Income> items = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = requireColumns(records.get(index), 7, index + 1);
            String id = uniqueId(row.get(0), ids, index + 1);
            if (incomeService.findById(id).isPresent()) throw duplicate(id);
            Account account = accountService.resolveAccount(row.get(4));
            items.add(new Income(id, date(row.get(1), index + 1),
                    amount(row.get(2), index + 1), row.get(3), account, row.get(6)));
        }
        return new ImportBatch("income", items.size(), () -> items.forEach(item ->
                incomeService.createIncomeWithId(item.getId(), item.getDate(),
                        item.getAmount(), item.getSource(), item.getAccount(),
                        item.getNote())));
    }

    private ImportBatch prepareTransfers(List<List<String>> records) {
        List<Transfer> items = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = requireColumns(records.get(index), 8, index + 1);
            String id = uniqueId(row.get(0), ids, index + 1);
            if (transferService.findById(id).isPresent()) throw duplicate(id);
            Account source = accountService.resolveAccount(row.get(3));
            Account destination = accountService.resolveAccount(row.get(5));
            items.add(new Transfer(id, date(row.get(1), index + 1),
                    amount(row.get(2), index + 1), source, destination, row.get(7)));
        }
        return new ImportBatch("transfers", items.size(), () ->
                items.forEach(item -> transferService.createTransferWithId(
                        item.getId(), item.getDate(), item.getAmount(),
                        item.getSourceAccount(), item.getDestinationAccount(),
                        item.getNote())));
    }

    private Path nextSafetyPath(Path parent) {
        if (parent == null) throw new ValidationException(
                "Import file must have a parent directory.");
        String base = "SpendWise-import-safety-"
                + SAFETY.format(java.time.LocalDateTime.now());
        for (int suffix = 0; suffix < 1000; suffix++) {
            Path candidate = parent.resolve(base
                    + (suffix == 0 ? "" : "-" + suffix) + ".zip")
                    .toAbsolutePath().normalize();
            if (Files.notExists(candidate)) return candidate;
        }
        throw new ValidationException("Could not choose a safety-backup name.");
    }

    private static List<List<String>> readCsv(Path path) {
        String text;
        try {
            if (Files.size(path) > 50L * 1024L * 1024L) {
                throw new ValidationException("CSV import is too large.");
            }
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw new com.spendwise.repository.RepositoryException(
                    "Could not read CSV import.", exception);
        }
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"'); index++;
                    } else quoted = false;
                } else field.append(current);
            } else if (current == '"' && field.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') index++;
                row.add(field.toString()); field.setLength(0);
                rows.add(List.copyOf(row)); row.clear();
            } else field.append(current);
        }
        if (quoted) throw new ValidationException("CSV contains an unclosed quote.");
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString()); rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static List<String> requireColumns(
            List<String> row, int expected, int record) {
        if (row.size() != expected) throw new ValidationException(
                "CSV record " + record + " has an unexpected column count.");
        return row;
    }
    private static String uniqueId(
            String id, Set<String> ids, int record) {
        if (!ids.add(id)) throw new ValidationException(
                "CSV record " + record + " duplicates ID " + id + ".");
        return id;
    }
    private static ValidationException duplicate(String id) {
        return new ValidationException(
                "A record with ID " + id + " already exists.");
    }

    private static LocalDate date(String value, int record) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ValidationException(
                    "CSV record " + record + " has an invalid date.");
        }
    }

    private static BigDecimal amount(String value, int record) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ValidationException(
                    "CSV record " + record + " has an invalid amount.");
        }
    }

    private record ImportBatch(String type, int size, Runnable action) {
        void apply() { action.run(); }
    }
}
