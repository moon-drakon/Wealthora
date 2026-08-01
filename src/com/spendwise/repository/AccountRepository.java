package com.spendwise.repository;

import com.spendwise.model.Account;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    /**
     * Returns persisted custom accounts in repository order.
     */
    List<Account> findAll();

    Optional<Account> findById(String identifier);

    void add(Account account);

    void update(Account account);
}
