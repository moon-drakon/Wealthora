package com.spendwise.service;

import com.spendwise.repository.CurrencyPreferenceRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public final class CurrencyService {

    public static final String DEFAULT_CURRENCY_CODE = "BDT";
    private final CurrencyPreferenceRepository repository;

    public CurrencyService(CurrencyPreferenceRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Currency getCurrency() {
        return currency(repository.findCurrencyCode()
                .orElse(DEFAULT_CURRENCY_CODE));
    }

    public Currency setCurrency(String currencyCode) {
        Currency selected = currency(currencyCode);
        repository.saveCurrencyCode(selected.getCurrencyCode());
        return selected;
    }

    public String format(BigDecimal amount) {
        BigDecimal required = Objects.requireNonNull(amount);
        return getCurrency().getCurrencyCode() + " "
                + required.setScale(2).toPlainString();
    }

    private static Currency currency(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("Currency code is required.");
        }
        try {
            return Currency.getInstance(code.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Unsupported ISO 4217 currency code.");
        }
    }
}
