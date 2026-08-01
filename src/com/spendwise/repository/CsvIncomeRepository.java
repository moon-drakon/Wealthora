package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.Income;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CsvIncomeRepository implements IncomeRepository {

    public static final String HEADER = "id,date,amount,source,account,note";
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "date", "amount", "source", "account", "note");
    private static final int COLUMN_COUNT = 6;

    private final java.nio.file.Path csvPath;
    private final Function<String, Account> accountResolver;

    public CsvIncomeRepository(
            java.nio.file.Path csvPath,
            Function<String, Account> accountResolver) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Income CSV path is required.")
                .toAbsolutePath()
                .normalize();
        this.accountResolver = Objects.requireNonNull(
                accountResolver, "Account resolver is required.");
    }

    @Override
    public List<Income> findAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "income");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return List.of();
        }
        return decode(content.orElseThrow());
    }

    @Override
    public Optional<Income> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(income -> income.getId().equals(id))
                .findFirst();
    }

    @Override
    public void add(Income income) {
        Income requiredIncome = Objects.requireNonNull(
                income, "Income is required.");
        List<Income> incomeEntries = new ArrayList<>(findAll());
        if (indexOf(incomeEntries, requiredIncome.getId()) >= 0) {
            throw new RepositoryException(
                    "Income ID already exists: " + requiredIncome.getId());
        }
        incomeEntries.add(requiredIncome);
        write(incomeEntries);
    }

    @Override
    public void update(Income income) {
        Income requiredIncome = Objects.requireNonNull(
                income, "Income is required.");
        List<Income> incomeEntries = new ArrayList<>(findAll());
        int index = indexOf(incomeEntries, requiredIncome.getId());
        if (index < 0) {
            throw new RepositoryException(
                    "Income ID does not exist: " + requiredIncome.getId());
        }
        incomeEntries.set(index, requiredIncome);
        write(incomeEntries);
    }

    @Override
    public boolean deleteById(String id) {
        List<Income> incomeEntries = new ArrayList<>(findAll());
        int index = indexOf(incomeEntries, id);
        if (index < 0) {
            return false;
        }
        incomeEntries.remove(index);
        write(incomeEntries);
        return true;
    }

    private List<Income> decode(String content) {
        List<List<String>> records =
                CsvFileSupport.parse(content, HEADER_FIELDS, "Income");
        List<Income> incomeEntries = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            int recordNumber = index + 1;
            List<String> fields = records.get(index);
            if (fields.size() != COLUMN_COUNT) {
                throw corrupt(recordNumber, "must contain exactly 6 columns.", null);
            }
            try {
                Income income = new Income(
                        fields.get(0),
                        LocalDate.parse(fields.get(1)),
                        new BigDecimal(fields.get(2)),
                        fields.get(3),
                        accountResolver.apply(fields.get(4)),
                        fields.get(5));
                if (!identifiers.add(income.getId())) {
                    throw corrupt(
                            recordNumber,
                            "contains duplicate income ID "
                            + income.getId() + ".",
                            null);
                }
                incomeEntries.add(income);
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw corrupt(
                        recordNumber,
                        "contains invalid data: " + safeMessage(exception),
                        exception);
            }
        }
        return List.copyOf(incomeEntries);
    }

    private void write(List<Income> incomeEntries) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (Income income : incomeEntries) {
            CsvFileSupport.appendField(csv, income.getId());
            csv.append(',');
            CsvFileSupport.appendField(csv, income.getDate().toString());
            csv.append(',');
            CsvFileSupport.appendField(csv, income.getAmount().toPlainString());
            csv.append(',');
            CsvFileSupport.appendField(csv, income.getSource());
            csv.append(',');
            CsvFileSupport.appendField(
                    csv, income.getAccount().getIdentifier());
            csv.append(',');
            CsvFileSupport.appendField(csv, income.getNote());
            csv.append('\n');
        }
        CsvFileSupport.write(
                csvPath, ".spendwise-income-", csv.toString(), "income");
    }

    private static int indexOf(List<Income> entries, String id) {
        if (id == null) {
            return -1;
        }
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).getId().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static RepositoryException corrupt(
            int recordNumber, String detail, RuntimeException cause) {
        String message = "Income CSV record " + recordNumber + " " + detail;
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
