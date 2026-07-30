package com.spendwise.repository;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CsvExpenseCodec {

    static final String HEADER = "id,description,amount,date,category,notes";
    private static final char UTF_8_BOM = '\uFEFF';
    private static final int COLUMN_COUNT = 6;
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "description", "amount", "date", "category", "notes");

    private CsvExpenseCodec() {
    }

    static String encode(List<Expense> expenses) {
        if (expenses == null) {
            throw new RepositoryException("Expense snapshot is required.");
        }

        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        Set<String> expenseIds = new HashSet<>();
        for (Expense expense : expenses) {
            if (expense == null) {
                throw new RepositoryException("Expense snapshot cannot contain null records.");
            }
            if (!expenseIds.add(expense.getId())) {
                throw new RepositoryException(
                        "Duplicate expense ID in snapshot: " + expense.getId());
            }

            appendField(csv, expense.getId());
            csv.append(',');
            appendField(csv, expense.getDescription());
            csv.append(',');
            appendField(csv, expense.getAmount().toPlainString());
            csv.append(',');
            appendField(csv, expense.getDate().toString());
            csv.append(',');
            appendField(csv, expense.getCategory().name());
            csv.append(',');
            appendField(csv, expense.getNotes());
            csv.append('\n');
        }
        return csv.toString();
    }

    static List<Expense> decode(String csvText) {
        if (csvText == null) {
            throw new RepositoryException("CSV text is required.");
        }
        if (csvText.isEmpty()) {
            return List.of();
        }

        String content = csvText;
        if (content.charAt(0) == UTF_8_BOM) {
            content = content.substring(1);
        }
        if (content.indexOf(UTF_8_BOM) >= 0) {
            throw new RepositoryException(
                    "A UTF-8 BOM is allowed only before the CSV header.");
        }
        if (!hasExactHeader(content)) {
            throw new RepositoryException("Expense CSV header must be exactly: " + HEADER);
        }

        List<List<String>> records = parseRecords(content);
        if (records.isEmpty()) {
            throw new RepositoryException("Expense CSV header is missing.");
        }
        if (!records.get(0).equals(HEADER_FIELDS)) {
            throw new RepositoryException("Expense CSV header must be exactly: " + HEADER);
        }

        List<Expense> expenses = new ArrayList<>();
        Set<String> expenseIds = new HashSet<>();
        for (int recordIndex = 1; recordIndex < records.size(); recordIndex++) {
            int recordNumber = recordIndex + 1;
            List<String> fields = records.get(recordIndex);
            if (fields.size() != COLUMN_COUNT) {
                throw new RepositoryException(
                        "CSV record " + recordNumber + " must contain exactly "
                        + COLUMN_COUNT + " columns.");
            }

            Expense expense = decodeExpense(fields, recordNumber);
            if (!expenseIds.add(expense.getId())) {
                throw new RepositoryException(
                        "CSV record " + recordNumber + " contains duplicate expense ID: "
                        + expense.getId());
            }
            expenses.add(expense);
        }
        return List.copyOf(expenses);
    }

    private static boolean hasExactHeader(String content) {
        return content.equals(HEADER)
                || content.startsWith(HEADER + "\n")
                || content.startsWith(HEADER + "\r\n");
    }

    private static Expense decodeExpense(List<String> fields, int recordNumber) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(fields.get(2));
        } catch (NumberFormatException exception) {
            throw invalidField(recordNumber, "amount", exception);
        }

        LocalDate date;
        try {
            date = LocalDate.parse(fields.get(3));
        } catch (DateTimeParseException exception) {
            throw invalidField(recordNumber, "date", exception);
        }

        Category category;
        try {
            category = Category.valueOf(fields.get(4));
        } catch (IllegalArgumentException exception) {
            throw invalidField(recordNumber, "category", exception);
        }

        try {
            return new Expense(
                    fields.get(0),
                    fields.get(1),
                    amount,
                    date,
                    category,
                    fields.get(5));
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException(
                    "CSV record " + recordNumber + " contains invalid expense data: "
                    + exception.getMessage(),
                    exception);
        }
    }

    private static RepositoryException invalidField(
            int recordNumber, String fieldName, RuntimeException cause) {
        return new RepositoryException(
                "CSV record " + recordNumber + " has an invalid " + fieldName + " value.",
                cause);
    }

    private static List<List<String>> parseRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotedField = false;
        boolean quotedFieldClosed = false;
        boolean fieldStarted = false;

        for (int index = 0; index < content.length(); index++) {
            char currentCharacter = content.charAt(index);
            int recordNumber = records.size() + 1;

            if (inQuotedField) {
                if (currentCharacter == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        currentField.append('"');
                        index++;
                    } else {
                        inQuotedField = false;
                        quotedFieldClosed = true;
                    }
                } else {
                    currentField.append(currentCharacter);
                }
                continue;
            }

            if (quotedFieldClosed) {
                if (currentCharacter == ',') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    quotedFieldClosed = false;
                    fieldStarted = false;
                } else if (currentCharacter == '\n') {
                    finishRecord(records, currentRecord, currentField);
                    quotedFieldClosed = false;
                    fieldStarted = false;
                } else if (currentCharacter == '\r') {
                    index = consumeCrLf(content, index, recordNumber);
                    finishRecord(records, currentRecord, currentField);
                    quotedFieldClosed = false;
                    fieldStarted = false;
                } else {
                    throw new RepositoryException(
                            "CSV record " + recordNumber
                            + " has illegal text after a closing quote.");
                }
                continue;
            }

            if (currentCharacter == '"') {
                if (fieldStarted || currentField.length() > 0) {
                    throw new RepositoryException(
                            "CSV record " + recordNumber + " has illegal quote placement.");
                }
                inQuotedField = true;
                fieldStarted = true;
            } else if (currentCharacter == ',') {
                currentRecord.add(currentField.toString());
                currentField.setLength(0);
                fieldStarted = false;
            } else if (currentCharacter == '\n') {
                finishRecord(records, currentRecord, currentField);
                fieldStarted = false;
            } else if (currentCharacter == '\r') {
                index = consumeCrLf(content, index, recordNumber);
                finishRecord(records, currentRecord, currentField);
                fieldStarted = false;
            } else {
                currentField.append(currentCharacter);
                fieldStarted = true;
            }
        }

        if (inQuotedField) {
            throw new RepositoryException(
                    "CSV record " + (records.size() + 1) + " contains an unclosed quoted field.");
        }
        if (quotedFieldClosed
                || fieldStarted
                || currentField.length() > 0
                || !currentRecord.isEmpty()) {
            finishRecord(records, currentRecord, currentField);
        }
        return records;
    }

    private static int consumeCrLf(String content, int carriageReturnIndex, int recordNumber) {
        if (carriageReturnIndex + 1 >= content.length()
                || content.charAt(carriageReturnIndex + 1) != '\n') {
            throw new RepositoryException(
                    "CSV record " + recordNumber + " uses an unsupported record ending.");
        }
        return carriageReturnIndex + 1;
    }

    private static void finishRecord(
            List<List<String>> records,
            List<String> currentRecord,
            StringBuilder currentField) {
        currentRecord.add(currentField.toString());
        records.add(List.copyOf(currentRecord));
        currentRecord.clear();
        currentField.setLength(0);
    }

    private static void appendField(StringBuilder csv, String value) {
        boolean requiresQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!requiresQuotes) {
            csv.append(value);
            return;
        }

        csv.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                csv.append("\"\"");
            } else {
                csv.append(character);
            }
        }
        csv.append('"');
    }
}
