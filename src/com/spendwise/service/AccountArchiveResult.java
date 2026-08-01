package com.spendwise.service;

import com.spendwise.model.Account;
import java.util.Objects;
import java.util.Optional;

public record AccountArchiveResult(
        Account archivedAccount, Optional<Account> replacementDefault) {

    public AccountArchiveResult {
        Objects.requireNonNull(archivedAccount, "Archived account is required.");
        replacementDefault = Objects.requireNonNull(
                replacementDefault, "Replacement default result is required.");
    }
}
