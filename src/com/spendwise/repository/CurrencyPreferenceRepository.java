package com.spendwise.repository;

import java.util.Optional;

public interface CurrencyPreferenceRepository {

    Optional<String> findCurrencyCode();

    void saveCurrencyCode(String currencyCode);
}
