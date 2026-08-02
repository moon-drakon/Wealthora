package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CsvAccountRepository implements AccountRepository {

    public static final String LEGACY_HEADER =
            "id,name,type,openingBalance,status";
    public static final String HEADER =
            "id,name,type,openingBalance,status,icon,color";
    private static final List<String> LEGACY_HEADER_FIELDS = List.of(
            "id", "name", "type", "openingBalance", "status");
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "name", "type", "openingBalance", "status", "icon", "color");

    private final java.nio.file.Path csvPath;

    public CsvAccountRepository(java.nio.file.Path csvPath) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Account CSV path is required.")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public List<Account> findAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "account");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return List.of();
        }
        return decode(content.orElseThrow());
    }

    @Override
    public Optional<Account> findById(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(account -> account.getIdentifier().equals(identifier))
                .findFirst();
    }

    @Override
    public void add(Account account) {
        Account requiredAccount = requireCustomAccount(account);
        List<Account> accounts = new ArrayList<>(findAll());
        rejectDuplicate(accounts, requiredAccount, false);
        accounts.add(requiredAccount);
        write(accounts);
    }

    @Override
    public void update(Account account) {
        Account requiredAccount = requireCustomAccount(account);
        List<Account> accounts = new ArrayList<>(findAll());
        int index = indexOf(accounts, requiredAccount.getIdentifier());
        if (index < 0) {
            throw new RepositoryException(
                    "Account ID does not exist: "
                    + requiredAccount.getIdentifier());
        }
        rejectDuplicate(accounts, requiredAccount, true);
        accounts.set(index, requiredAccount);
        write(accounts);
    }

    private List<Account> decode(String content) {
        boolean legacy = startsWithHeader(content, LEGACY_HEADER);
        List<List<String>> records = CsvFileSupport.parse(
                content,
                legacy ? LEGACY_HEADER_FIELDS : HEADER_FIELDS,
                "Account");
        List<Account> accounts = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        Set<String> names = new HashSet<>();
        names.add(Account.DEFAULT.getDisplayName().toLowerCase(Locale.ROOT));
        for (int index = 1; index < records.size(); index++) {
            int recordNumber = index + 1;
            List<String> fields = records.get(index);
            if (fields.size() != (legacy ? 5 : 7)) {
                throw corrupt(recordNumber, "has an unexpected column count.");
            }
            try {
                Account account = Account.createCustom(
                        fields.get(0),
                        fields.get(1),
                        AccountType.fromStoredValue(fields.get(2)),
                        new BigDecimal(fields.get(3)),
                        legacy ? Account.DEFAULT_ICON : fields.get(5),
                        legacy ? Account.DEFAULT_COLOR : fields.get(6),
                        parseArchived(fields.get(4), recordNumber));
                String normalizedName =
                        account.getDisplayName().toLowerCase(Locale.ROOT);
                if (!identifiers.add(account.getIdentifier())) {
                    throw corrupt(
                            recordNumber,
                            "contains duplicate account ID "
                            + account.getIdentifier() + ".");
                }
                if (!names.add(normalizedName)) {
                    throw corrupt(
                            recordNumber,
                            "contains duplicate account name "
                            + account.getDisplayName() + ".");
                }
                accounts.add(account);
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new RepositoryException(
                        "Account CSV record " + recordNumber
                        + " contains invalid data: "
                        + safeMessage(exception),
                        exception);
            }
        }
        return List.copyOf(accounts);
    }

    private void write(List<Account> accounts) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (Account account : accounts) {
            CsvFileSupport.appendField(csv, account.getIdentifier());
            csv.append(',');
            CsvFileSupport.appendField(csv, account.getDisplayName());
            csv.append(',');
            CsvFileSupport.appendField(csv, account.getType().name());
            csv.append(',');
            CsvFileSupport.appendField(
                    csv, account.getOpeningBalance().toPlainString());
            csv.append(',');
            CsvFileSupport.appendField(
                    csv, account.isArchived() ? "ARCHIVED" : "ACTIVE");
            csv.append(',');
            CsvFileSupport.appendField(csv, account.getIconName());
            csv.append(',');
            CsvFileSupport.appendField(csv, account.getColorHex());
            csv.append('\n');
        }
        CsvFileSupport.write(
                csvPath, ".spendwise-accounts-", csv.toString(), "account");
    }

    private static boolean parseArchived(String value, int recordNumber) {
        return switch (value) {
            case "ACTIVE" -> false;
            case "ARCHIVED" -> true;
            default -> throw corrupt(
                    recordNumber, "has an invalid account status.");
        };
    }

    private static void rejectDuplicate(
            List<Account> accounts,
            Account candidate,
            boolean ignoreMatchingIdentifier) {
        String candidateName =
                candidate.getDisplayName().toLowerCase(Locale.ROOT);
        if (candidateName.equals(
                Account.DEFAULT.getDisplayName().toLowerCase(Locale.ROOT))) {
            throw new RepositoryException(
                    "Account name already exists: "
                    + candidate.getDisplayName());
        }
        for (Account account : accounts) {
            if (account.getIdentifier().equals(candidate.getIdentifier())) {
                if (ignoreMatchingIdentifier) {
                    continue;
                }
                throw new RepositoryException(
                        "Account ID already exists: "
                        + candidate.getIdentifier());
            }
            if (account.getDisplayName().toLowerCase(Locale.ROOT)
                    .equals(candidateName)) {
                throw new RepositoryException(
                        "Account name already exists: "
                        + candidate.getDisplayName());
            }
        }
    }

    private static Account requireCustomAccount(Account account) {
        Account requiredAccount = Objects.requireNonNull(
                account, "Account is required.");
        if (requiredAccount.isProtected()) {
            throw new RepositoryException(
                    "The protected default account is not persisted.");
        }
        return requiredAccount;
    }

    private static int indexOf(List<Account> accounts, String identifier) {
        for (int index = 0; index < accounts.size(); index++) {
            if (accounts.get(index).getIdentifier().equals(identifier)) {
                return index;
            }
        }
        return -1;
    }

    private static RepositoryException corrupt(
            int recordNumber, String detail) {
        return new RepositoryException(
                "Account CSV record " + recordNumber + " " + detail);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "invalid value"
                : message;
    }

    private static boolean startsWithHeader(String content, String header) {
        String normalized = content.startsWith("\uFEFF")
                ? content.substring(1) : content;
        return normalized.equals(header)
                || normalized.startsWith(header + "\n")
                || normalized.startsWith(header + "\r\n");
    }
}
