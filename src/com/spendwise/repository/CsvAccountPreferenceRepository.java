package com.spendwise.repository;

import com.spendwise.validation.FinanceValidator;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CsvAccountPreferenceRepository
        implements AccountPreferenceRepository {

    public static final String HEADER = "key,value";
    private static final List<String> HEADER_FIELDS = List.of("key", "value");
    private static final String DEFAULT_KEY = "DEFAULT_ACCOUNT";

    private final Path csvPath;

    public CsvAccountPreferenceRepository(Path csvPath) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Account settings CSV path is required.")
                .toAbsolutePath().normalize();
    }

    @Override
    public Optional<String> findDefaultAccountId() {
        Optional<String> content = CsvFileSupport.read(
                csvPath, "account settings");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return Optional.empty();
        }
        List<List<String>> records = CsvFileSupport.parse(
                content.orElseThrow(), HEADER_FIELDS, "Account settings");
        if (records.size() != 2 || records.get(1).size() != 2
                || !DEFAULT_KEY.equals(records.get(1).get(0))) {
            throw new RepositoryException(
                    "Account settings CSV must contain exactly one "
                    + DEFAULT_KEY + " record.");
        }
        try {
            return Optional.of(FinanceValidator.validateIdentifier(
                    records.get(1).get(1), "Default account", "ACCOUNT_"));
        } catch (RuntimeException exception) {
            throw new RepositoryException(
                    "Account settings CSV contains an invalid default account ID.",
                    exception);
        }
    }

    @Override
    public void saveDefaultAccountId(String identifier) {
        String normalized = FinanceValidator.validateIdentifier(
                identifier, "Default account", "ACCOUNT_");
        if (java.nio.file.Files.exists(csvPath)) {
            findDefaultAccountId();
        }
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        CsvFileSupport.appendField(csv, DEFAULT_KEY);
        csv.append(',');
        CsvFileSupport.appendField(csv, normalized);
        csv.append('\n');
        CsvFileSupport.write(
                csvPath,
                ".spendwise-account-settings-",
                csv.toString(),
                "account settings");
    }
}
