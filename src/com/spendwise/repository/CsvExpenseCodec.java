package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

final class CsvExpenseCodec {

    static final String HEADER = "id,description,amount,date,category,notes";
    static final String ACCOUNT_HEADER =
            "id,description,amount,date,category,account,notes";
    private static final List<String> LEGACY_HEADER_FIELDS = List.of(
            "id", "description", "amount", "date", "category", "notes");
    private static final List<String> ACCOUNT_HEADER_FIELDS = List.of(
            "id", "description", "amount", "date",
            "category", "account", "notes");

    private CsvExpenseCodec() {
    }

    static String encode(List<Expense> expenses) {
        if (expenses == null) {
            throw new RepositoryException("Expense snapshot is required.");
        }
        boolean accountAware = expenses.stream()
                .filter(Objects::nonNull)
                .anyMatch(expense -> !Account.DEFAULT_IDENTIFIER.equals(
                        expense.getAccount().getIdentifier()));
        StringBuilder csv = new StringBuilder(
                accountAware ? ACCOUNT_HEADER : HEADER).append('\n');
        Set<String> identifiers = new HashSet<>();
        for (Expense expense : expenses) {
            if (expense == null) {
                throw new RepositoryException(
                        "Expense snapshot cannot contain null records.");
            }
            if (!identifiers.add(expense.getId())) {
                throw new RepositoryException(
                        "Duplicate expense ID in snapshot: " + expense.getId());
            }
            appendCommonFields(csv, expense);
            csv.append(',');
            if (accountAware) {
                CsvFileSupport.appendField(
                        csv, expense.getAccount().getIdentifier());
                csv.append(',');
            }
            CsvFileSupport.appendField(csv, expense.getNotes());
            csv.append('\n');
        }
        return csv.toString();
    }

    static List<Expense> decode(String csvText) {
        return decode(csvText, Category::valueOf, CsvExpenseCodec::defaultAccount);
    }

    static List<Expense> decode(
            String csvText, Function<String, Category> categoryResolver) {
        return decode(csvText, categoryResolver, CsvExpenseCodec::defaultAccount);
    }

    static List<Expense> decode(
            String csvText,
            Function<String, Category> categoryResolver,
            Function<String, Account> accountResolver) {
        if (csvText == null) {
            throw new RepositoryException("CSV text is required.");
        }
        Objects.requireNonNull(
                categoryResolver, "Category resolver is required.");
        Objects.requireNonNull(accountResolver, "Account resolver is required.");
        if (csvText.isEmpty()) {
            return List.of();
        }

        boolean accountAware = beginsWithHeader(csvText, ACCOUNT_HEADER);
        List<String> expectedHeader =
                accountAware ? ACCOUNT_HEADER_FIELDS : LEGACY_HEADER_FIELDS;
        if (!accountAware && !beginsWithHeader(csvText, HEADER)) {
            throw new RepositoryException(
                    "Expense CSV header must be exactly " + HEADER
                    + " or " + ACCOUNT_HEADER + ".");
        }

        List<List<String>> records =
                CsvFileSupport.parse(csvText, expectedHeader, "Expense");
        List<Expense> expenses = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            int recordNumber = index + 1;
            List<String> fields = records.get(index);
            int expectedColumns = accountAware ? 7 : 6;
            if (fields.size() != expectedColumns) {
                throw new RepositoryException(
                        "Expense CSV record " + recordNumber
                        + " must contain exactly " + expectedColumns
                        + " columns.");
            }
            Expense expense = decodeExpense(
                    fields,
                    recordNumber,
                    accountAware,
                    categoryResolver,
                    accountResolver);
            if (!identifiers.add(expense.getId())) {
                throw new RepositoryException(
                        "Expense CSV record " + recordNumber
                        + " contains duplicate expense ID: "
                        + expense.getId());
            }
            expenses.add(expense);
        }
        return List.copyOf(expenses);
    }

    private static Expense decodeExpense(
            List<String> fields,
            int recordNumber,
            boolean accountAware,
            Function<String, Category> categoryResolver,
            Function<String, Account> accountResolver) {
        try {
            BigDecimal amount = new BigDecimal(fields.get(2));
            LocalDate date = LocalDate.parse(fields.get(3));
            Category category = categoryResolver.apply(fields.get(4));
            Account account = accountAware
                    ? accountResolver.apply(fields.get(5))
                    : Account.DEFAULT;
            String notes = fields.get(accountAware ? 6 : 5);
            return new Expense(
                    fields.get(0),
                    fields.get(1),
                    amount,
                    date,
                    category,
                    account,
                    notes);
        } catch (RuntimeException exception) {
            throw new RepositoryException(
                    "Expense CSV record " + recordNumber
                    + " contains invalid data: " + safeMessage(exception),
                    exception);
        }
    }

    private static void appendCommonFields(
            StringBuilder csv, Expense expense) {
        CsvFileSupport.appendField(csv, expense.getId());
        csv.append(',');
        CsvFileSupport.appendField(csv, expense.getDescription());
        csv.append(',');
        CsvFileSupport.appendField(
                csv, expense.getAmount().toPlainString());
        csv.append(',');
        CsvFileSupport.appendField(csv, expense.getDate().toString());
        csv.append(',');
        CsvFileSupport.appendField(
                csv, expense.getCategory().getIdentifier());
    }

    private static boolean beginsWithHeader(String csvText, String header) {
        String content = csvText.startsWith("\uFEFF")
                ? csvText.substring(1)
                : csvText;
        return content.equals(header)
                || content.startsWith(header + "\n")
                || content.startsWith(header + "\r\n");
    }

    private static Account defaultAccount(String identifier) {
        if (Account.DEFAULT_IDENTIFIER.equals(identifier)) {
            return Account.DEFAULT;
        }
        throw new IllegalArgumentException(
                "Unknown account identifier: " + identifier);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "invalid value"
                : message;
    }
}
