package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CsvTransferRepository implements TransferRepository {

    public static final String LEGACY_HEADER =
            "id,date,amount,sourceAccount,destinationAccount,note";
    public static final String HEADER =
            "id,date,amount,sourceAccount,destinationAccount,tags,note";
    private static final List<String> LEGACY_HEADER_FIELDS = List.of(
            "id", "date", "amount", "sourceAccount",
            "destinationAccount", "note");
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "date", "amount", "sourceAccount",
            "destinationAccount", "tags", "note");

    private final java.nio.file.Path csvPath;
    private final Function<String, Account> accountResolver;

    public CsvTransferRepository(
            java.nio.file.Path csvPath,
            Function<String, Account> accountResolver) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Transfer CSV path is required.")
                .toAbsolutePath()
                .normalize();
        this.accountResolver = Objects.requireNonNull(
                accountResolver, "Account resolver is required.");
    }

    @Override
    public List<Transfer> findAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "transfer");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return List.of();
        }
        return decode(content.orElseThrow());
    }

    @Override
    public Optional<Transfer> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(transfer -> transfer.getId().equals(id))
                .findFirst();
    }

    @Override
    public void add(Transfer transfer) {
        Transfer requiredTransfer = Objects.requireNonNull(
                transfer, "Transfer is required.");
        List<Transfer> transfers = new ArrayList<>(findAll());
        if (indexOf(transfers, requiredTransfer.getId()) >= 0) {
            throw new RepositoryException(
                    "Transfer ID already exists: " + requiredTransfer.getId());
        }
        transfers.add(requiredTransfer);
        write(transfers);
    }

    @Override
    public void update(Transfer transfer) {
        Transfer requiredTransfer = Objects.requireNonNull(
                transfer, "Transfer is required.");
        List<Transfer> transfers = new ArrayList<>(findAll());
        int index = indexOf(transfers, requiredTransfer.getId());
        if (index < 0) {
            throw new RepositoryException(
                    "Transfer ID does not exist: "
                    + requiredTransfer.getId());
        }
        transfers.set(index, requiredTransfer);
        write(transfers);
    }

    @Override
    public boolean deleteById(String id) {
        List<Transfer> transfers = new ArrayList<>(findAll());
        int index = indexOf(transfers, id);
        if (index < 0) {
            return false;
        }
        transfers.remove(index);
        write(transfers);
        return true;
    }

    private List<Transfer> decode(String content) {
        boolean legacy = startsWithHeader(content, LEGACY_HEADER);
        List<List<String>> records = CsvFileSupport.parse(
                content, legacy ? LEGACY_HEADER_FIELDS : HEADER_FIELDS, "Transfer");
        List<Transfer> transfers = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            int recordNumber = index + 1;
            List<String> fields = records.get(index);
            if (fields.size() != (legacy ? 6 : 7)) {
                throw corrupt(recordNumber, "has an unexpected column count.", null);
            }
            try {
                Transfer transfer = new Transfer(
                        fields.get(0),
                        LocalDate.parse(fields.get(1)),
                        new BigDecimal(fields.get(2)),
                        accountResolver.apply(fields.get(3)),
                        accountResolver.apply(fields.get(4)),
                        legacy ? List.of() : parseTags(fields.get(5)),
                        fields.get(legacy ? 5 : 6));
                if (!identifiers.add(transfer.getId())) {
                    throw corrupt(
                            recordNumber,
                            "contains duplicate transfer ID "
                            + transfer.getId() + ".",
                            null);
                }
                transfers.add(transfer);
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw corrupt(
                        recordNumber,
                        "contains invalid data: " + safeMessage(exception),
                        exception);
            }
        }
        return List.copyOf(transfers);
    }

    private void write(List<Transfer> transfers) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (Transfer transfer : transfers) {
            CsvFileSupport.appendField(csv, transfer.getId());
            csv.append(',');
            CsvFileSupport.appendField(csv, transfer.getDate().toString());
            csv.append(',');
            CsvFileSupport.appendField(
                    csv, transfer.getAmount().toPlainString());
            csv.append(',');
            CsvFileSupport.appendField(
                    csv, transfer.getSourceAccount().getIdentifier());
            csv.append(',');
            CsvFileSupport.appendField(
                    csv, transfer.getDestinationAccount().getIdentifier());
            csv.append(',');
            CsvFileSupport.appendField(csv, String.join("|", transfer.getTags()));
            csv.append(',');
            CsvFileSupport.appendField(csv, transfer.getNote());
            csv.append('\n');
        }
        CsvFileSupport.write(
                csvPath,
                ".spendwise-transfers-",
                csv.toString(),
                "transfer");
    }

    private static int indexOf(List<Transfer> transfers, String id) {
        if (id == null) {
            return -1;
        }
        for (int index = 0; index < transfers.size(); index++) {
            if (transfers.get(index).getId().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static RepositoryException corrupt(
            int recordNumber, String detail, RuntimeException cause) {
        String message = "Transfer CSV record " + recordNumber + " " + detail;
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

    private static List<String> parseTags(String value) {
        return value.isEmpty() ? List.of() : List.of(value.split("\\|", -1));
    }

    private static boolean startsWithHeader(String content, String header) {
        String normalized = content.startsWith("\uFEFF")
                ? content.substring(1) : content;
        return normalized.equals(header)
                || normalized.startsWith(header + "\n")
                || normalized.startsWith(header + "\r\n");
    }
}
