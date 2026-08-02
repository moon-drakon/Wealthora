package com.spendwise.auth.local;

import java.util.List;
import java.util.Optional;

public interface LocalUserRepository {

    List<LocalUserRecord> findAll();

    Optional<LocalUserRecord> findById(String userIdentifier);

    Optional<LocalUserRecord> findByEmail(String normalizedEmail);

    Optional<LocalUserRecord> findOwner();

    void save(LocalUserRecord record);
}
