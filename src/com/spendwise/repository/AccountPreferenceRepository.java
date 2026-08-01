package com.spendwise.repository;

import java.util.Optional;

public interface AccountPreferenceRepository {

    Optional<String> findDefaultAccountId();

    void saveDefaultAccountId(String identifier);
}
