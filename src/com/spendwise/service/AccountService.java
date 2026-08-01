package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class AccountService {

    private static final String ID_PREFIX = "ACCOUNT_";

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = Objects.requireNonNull(
                repository, "Account repository is required.");
    }

    public List<Account> listAllAccounts() {
        List<Account> accounts = new ArrayList<>();
        accounts.add(Account.DEFAULT);
        accounts.addAll(repository.findAll());
        return List.copyOf(accounts);
    }

    public List<Account> listSelectableAccounts() {
        return listAllAccounts().stream()
                .filter(Account::isActive)
                .toList();
    }

    public Account resolveAccount(String identifier) {
        String normalized = FinanceValidator.validateIdentifier(
                identifier, "Account", ID_PREFIX);
        if (Account.DEFAULT_IDENTIFIER.equals(normalized)) {
            return Account.DEFAULT;
        }
        return repository.findById(normalized)
                .orElseThrow(() -> new RepositoryException(
                    "Unknown account identifier in stored data: "
                    + normalized));
    }

    public Account addAccount(
            String displayName,
            AccountType type,
            BigDecimal openingBalance) {
        String normalizedName = FinanceValidator.validateRequiredText(
                displayName,
                "Account name",
                FinanceValidator.MAX_NAME_LENGTH);
        rejectDuplicateName(normalizedName, null);
        Account account = Account.createCustom(
                ID_PREFIX + UUID.randomUUID().toString()
                        .replace("-", "")
                        .toUpperCase(Locale.ROOT),
                normalizedName,
                Objects.requireNonNull(type, "Account type is required."),
                openingBalance,
                false);
        repository.add(account);
        return account;
    }

    public Account renameAccount(
            String identifier, String newDisplayName) {
        Account existing = requireCustomAccount(identifier);
        String normalizedName = FinanceValidator.validateRequiredText(
                newDisplayName,
                "Account name",
                FinanceValidator.MAX_NAME_LENGTH);
        rejectDuplicateName(normalizedName, existing.getIdentifier());
        Account renamed = existing.withDisplayName(normalizedName);
        repository.update(renamed);
        return renamed;
    }

    public Account archiveAccount(String identifier) {
        Account existing = requireCustomAccount(identifier);
        if (existing.isArchived()) {
            return existing;
        }
        Account archived = existing.withArchived(true);
        repository.update(archived);
        return archived;
    }

    public Account restoreAccount(String identifier) {
        Account existing = requireCustomAccount(identifier);
        if (existing.isActive()) {
            return existing;
        }
        Account restored = existing.withArchived(false);
        repository.update(restored);
        return restored;
    }

    public Account requireSelectable(Account account) {
        Account required = Objects.requireNonNull(
                account, "Account is required.");
        Account current = resolveAccount(required.getIdentifier());
        if (!current.isActive()) {
            throw new ValidationException(
                    "Archived accounts cannot be used for new transactions.");
        }
        return current;
    }

    public Account requireSelectableOrHistorical(
            Account requested, Account historical) {
        Account required = Objects.requireNonNull(
                requested, "Account is required.");
        if (historical != null
                && required.getIdentifier().equals(
                        historical.getIdentifier())) {
            return resolveAccount(required.getIdentifier());
        }
        return requireSelectable(required);
    }

    private Account requireCustomAccount(String identifier) {
        Account account = resolveAccount(identifier);
        if (account.isProtected()) {
            throw new ValidationException(
                    "The protected default account cannot be changed.");
        }
        return account;
    }

    private void rejectDuplicateName(
            String displayName, String ignoredIdentifier) {
        String normalizedName = displayName.toLowerCase(Locale.ROOT);
        for (Account account : listAllAccounts()) {
            if ((ignoredIdentifier == null
                    || !account.getIdentifier().equals(ignoredIdentifier))
                    && account.getDisplayName()
                            .toLowerCase(Locale.ROOT)
                            .equals(normalizedName)) {
                throw new ValidationException(
                        "An account named \"" + displayName
                        + "\" already exists.");
            }
        }
    }
}
