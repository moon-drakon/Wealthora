package com.spendwise.repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CsvCurrencyPreferenceRepository
        implements CurrencyPreferenceRepository {

    private static final String HEADER = "currency";
    private static final List<String> HEADER_FIELDS = List.of(HEADER);
    private final java.nio.file.Path csvPath;

    public CsvCurrencyPreferenceRepository(java.nio.file.Path csvPath) {
        this.csvPath = Objects.requireNonNull(csvPath).toAbsolutePath().normalize();
    }

    @Override
    public Optional<String> findCurrencyCode() {
        Optional<String> content = CsvFileSupport.read(csvPath, "currency setting");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return Optional.empty();
        }
        List<List<String>> records = CsvFileSupport.parse(
                content.orElseThrow(), HEADER_FIELDS, "Currency setting");
        if (records.size() != 2 || records.get(1).size() != 1) {
            throw new RepositoryException(
                    "Currency settings CSV must contain exactly one setting.");
        }
        return Optional.of(records.get(1).get(0));
    }

    @Override
    public void saveCurrencyCode(String currencyCode) {
        String required = Objects.requireNonNull(currencyCode);
        CsvFileSupport.write(csvPath, ".spendwise-currency-",
                HEADER + "\n" + required + "\n", "currency setting");
    }
}
