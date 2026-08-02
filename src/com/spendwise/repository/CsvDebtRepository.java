package com.spendwise.repository;

import com.spendwise.model.DebtDirection;
import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CsvDebtRepository implements DebtRepository {
    public static final String HEADER = "recordType,id,debtId,direction,"
            + "counterparty,originalAmount,dueDate,date,amount,note";
    private static final List<String> HEADER_FIELDS = List.of(
            "recordType", "id", "debtId", "direction", "counterparty",
            "originalAmount", "dueDate", "date", "amount", "note");
    private final Path csvPath;

    public CsvDebtRepository(Path csvPath) {
        this.csvPath = Objects.requireNonNull(csvPath)
                .toAbsolutePath().normalize();
    }

    @Override
    public List<DebtRecord> findAllDebts() { return read().debts(); }

    @Override
    public Optional<DebtRecord> findDebtById(String identifier) {
        return findAllDebts().stream().filter(debt ->
                debt.getIdentifier().equals(identifier)).findFirst();
    }

    @Override
    public List<DebtRepayment> findRepayments(String debtIdentifier) {
        return read().repayments().stream()
                .filter(item -> item.getDebtIdentifier().equals(debtIdentifier))
                .sorted(java.util.Comparator.comparing(DebtRepayment::getDate)
                        .thenComparing(DebtRepayment::getIdentifier))
                .toList();
    }

    @Override
    public void addDebt(DebtRecord debt) {
        DebtRecord required = Objects.requireNonNull(debt);
        Snapshot snapshot = read();
        if (snapshot.debts().stream().anyMatch(item -> item.getIdentifier()
                .equals(required.getIdentifier()))) {
            throw new RepositoryException("Debt ID already exists.");
        }
        List<DebtRecord> debts = new ArrayList<>(snapshot.debts());
        debts.add(required);
        write(new Snapshot(debts, snapshot.repayments()));
    }

    @Override
    public void updateDebt(DebtRecord debt) {
        DebtRecord required = Objects.requireNonNull(debt);
        Snapshot snapshot = read();
        List<DebtRecord> debts = new ArrayList<>(snapshot.debts());
        int index = -1;
        for (int current = 0; current < debts.size(); current++) {
            if (debts.get(current).getIdentifier()
                    .equals(required.getIdentifier())) {
                index = current; break;
            }
        }
        if (index < 0) throw new RepositoryException("Debt was not found.");
        debts.set(index, required);
        write(new Snapshot(debts, snapshot.repayments()));
    }

    @Override
    public void addRepayment(DebtRepayment repayment) {
        DebtRepayment required = Objects.requireNonNull(repayment);
        Snapshot snapshot = read();
        if (snapshot.debts().stream().noneMatch(debt -> debt.getIdentifier()
                .equals(required.getDebtIdentifier()))) {
            throw new RepositoryException("Repayment references an unknown debt.");
        }
        if (snapshot.repayments().stream().anyMatch(item -> item.getIdentifier()
                .equals(required.getIdentifier()))) {
            throw new RepositoryException("Repayment ID already exists.");
        }
        List<DebtRepayment> repayments = new ArrayList<>(snapshot.repayments());
        repayments.add(required);
        write(new Snapshot(snapshot.debts(), repayments));
    }

    private Snapshot read() {
        Optional<String> content = CsvFileSupport.read(csvPath, "debt");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return new Snapshot(List.of(), List.of());
        }
        List<List<String>> records = CsvFileSupport.parse(
                content.orElseThrow(), HEADER_FIELDS, "Debt");
        List<DebtRecord> debts = new ArrayList<>();
        List<DebtRepayment> repayments = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> fields = records.get(index);
            if (fields.size() != 10) {
                throw corrupt(index + 1, "must contain exactly 10 columns.", null);
            }
            try {
                if (!identifiers.add(fields.get(1))) {
                    throw corrupt(index + 1, "duplicates a record ID.", null);
                }
                switch (fields.get(0)) {
                    case "DEBT" -> debts.add(new DebtRecord(
                            fields.get(1), DebtDirection.valueOf(fields.get(3)),
                            fields.get(4), new BigDecimal(fields.get(5)),
                            LocalDate.parse(fields.get(6)), fields.get(9)));
                    case "REPAYMENT" -> repayments.add(new DebtRepayment(
                            fields.get(1), fields.get(2),
                            LocalDate.parse(fields.get(7)),
                            new BigDecimal(fields.get(8)), fields.get(9)));
                    default -> throw corrupt(index + 1,
                            "has an unsupported record type.", null);
                }
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw corrupt(index + 1,
                        "contains invalid data: " + safeMessage(exception),
                        exception);
            }
        }
        Set<String> debtIds = debts.stream().map(DebtRecord::getIdentifier)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (repayments.stream().anyMatch(item ->
                !debtIds.contains(item.getDebtIdentifier()))) {
            throw new RepositoryException("Debt data contains an orphan repayment.");
        }
        return new Snapshot(debts, repayments);
    }

    private void write(Snapshot snapshot) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        snapshot.debts().stream().sorted(java.util.Comparator
                .comparing(DebtRecord::getDueDate)
                .thenComparing(DebtRecord::getIdentifier))
                .forEach(debt -> {
                    append(csv, "DEBT"); append(csv, debt.getIdentifier());
                    append(csv, ""); append(csv, debt.getDirection().name());
                    append(csv, debt.getCounterparty());
                    append(csv, debt.getOriginalAmount().toPlainString());
                    append(csv, debt.getDueDate().toString());
                    append(csv, ""); append(csv, "");
                    CsvFileSupport.appendField(csv, debt.getNote());
                    csv.append('\n');
                });
        snapshot.repayments().stream().sorted(java.util.Comparator
                .comparing(DebtRepayment::getDate)
                .thenComparing(DebtRepayment::getIdentifier))
                .forEach(item -> {
                    append(csv, "REPAYMENT"); append(csv, item.getIdentifier());
                    append(csv, item.getDebtIdentifier());
                    append(csv, ""); append(csv, ""); append(csv, "");
                    append(csv, ""); append(csv, item.getDate().toString());
                    append(csv, item.getAmount().toPlainString());
                    CsvFileSupport.appendField(csv, item.getNote());
                    csv.append('\n');
                });
        CsvFileSupport.write(csvPath, ".spendwise-debts-", csv.toString(),
                "debt");
    }

    private static void append(StringBuilder csv, String value) {
        CsvFileSupport.appendField(csv, value); csv.append(',');
    }
    private static RepositoryException corrupt(
            int record, String detail, RuntimeException cause) {
        String message = "Debt CSV record " + record + " " + detail;
        return cause == null ? new RepositoryException(message)
                : new RepositoryException(message, cause);
    }
    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "invalid value"
                : exception.getMessage();
    }
    private record Snapshot(
            List<DebtRecord> debts, List<DebtRepayment> repayments) {
        Snapshot {
            debts = List.copyOf(debts); repayments = List.copyOf(repayments);
        }
    }
}
