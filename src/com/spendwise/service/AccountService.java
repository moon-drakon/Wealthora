package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.AccountPreferenceRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AccountService {

    private static final String ID_PREFIX = "ACCOUNT_";

    private final AccountRepository repository;
    private final AccountPreferenceRepository preferenceRepository;

    public AccountService(AccountRepository repository) {
        this(repository, new MemoryAccountPreferenceRepository());
    }

    public AccountService(
            AccountRepository repository,
            AccountPreferenceRepository preferenceRepository) {
        this.repository = Objects.requireNonNull(
                repository, "Account repository is required.");
        this.preferenceRepository = Objects.requireNonNull(
                preferenceRepository,
                "Account preference repository is required.");
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

    Account addAccountWithId(
            String identifier,
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            String iconName,
            String colorHex) {
        String normalizedId = FinanceValidator.validateIdentifier(
                identifier, "Account", ID_PREFIX);
        if (repository.findById(normalizedId).isPresent()) {
            throw new ValidationException(
                    "An account with the requested identifier already exists.");
        }
        String normalizedName = FinanceValidator.validateRequiredText(
                displayName, "Account name", FinanceValidator.MAX_NAME_LENGTH);
        rejectDuplicateName(normalizedName, null);
        Account account = Account.createCustom(
                normalizedId, normalizedName,
                Objects.requireNonNull(type, "Account type is required."),
                openingBalance, iconName, colorHex,
                Account.DEFAULT_CURRENCY_CODE, "", LocalDate.now(), false);
        repository.add(account);
        return account;
    }

    public Account addAccount(
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            String iconName,
            String colorHex) {
        return addAccount(displayName, type, openingBalance, iconName,
                colorHex, Account.DEFAULT_CURRENCY_CODE, "");
    }

    public Account addAccount(
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            String iconName,
            String colorHex,
            String currencyCode,
            String institutionName) {
        String normalizedName = FinanceValidator.validateRequiredText(
                displayName, "Account name", FinanceValidator.MAX_NAME_LENGTH);
        rejectDuplicateName(normalizedName, null);
        Account account = Account.createCustom(
                ID_PREFIX + UUID.randomUUID().toString()
                        .replace("-", "").toUpperCase(Locale.ROOT),
                normalizedName,
                Objects.requireNonNull(type, "Account type is required."),
                openingBalance,
                iconName,
                colorHex,
                currencyCode,
                institutionName,
                java.time.LocalDate.now(),
                false);
        repository.add(account);
        return account;
    }

    public Account renameAccount(
            String identifier, String newDisplayName) {
        Account existing = requireCustomAccount(identifier);
        return updateAccountMetadata(
                identifier, newDisplayName, existing.getType());
    }

    public Account updateAccountMetadata(
            String identifier,
            String newDisplayName,
            AccountType newType) {
        Account existing = requireCustomAccount(identifier);
        String normalizedName = FinanceValidator.validateRequiredText(
                newDisplayName,
                "Account name",
                FinanceValidator.MAX_NAME_LENGTH);
        rejectDuplicateName(normalizedName, existing.getIdentifier());
        Account replacement = existing.withMetadata(
                normalizedName,
                Objects.requireNonNull(newType, "Account type is required."));
        repository.update(replacement);
        return replacement;
    }

    public Account updateAccountDetails(
            String identifier,
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            String iconName,
            String colorHex) {
        Account existing = requireCustomAccount(identifier);
        return updateAccountDetails(identifier, displayName, type,
                openingBalance, iconName, colorHex,
                existing.getCurrencyCode(), existing.getInstitutionName());
    }

    public Account updateAccountDetails(
            String identifier,
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            String iconName,
            String colorHex,
            String currencyCode,
            String institutionName) {
        Account existing = requireCustomAccount(identifier);
        String normalizedName = FinanceValidator.validateRequiredText(
                displayName, "Account name", FinanceValidator.MAX_NAME_LENGTH);
        rejectDuplicateName(normalizedName, existing.getIdentifier());
        Account replacement = existing.withDetails(
                normalizedName, type, openingBalance, iconName, colorHex,
                currencyCode, institutionName);
        repository.update(replacement);
        return replacement;
    }

    public Account archiveAccount(String identifier) {
        return archiveAccountWithResult(identifier).archivedAccount();
    }

    public AccountArchiveResult archiveAccountWithResult(String identifier) {
        Account existing = requireCustomAccount(identifier);
        if (existing.isArchived()) {
            return new AccountArchiveResult(existing, Optional.empty());
        }
        Optional<Account> replacementDefault = Optional.empty();
        if (getDefaultAccount().equals(existing)) {
            Account replacement = listSelectableAccounts().stream()
                    .filter(account -> !account.equals(existing))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException(
                            "At least one active account must remain."));
            preferenceRepository.saveDefaultAccountId(
                    replacement.getIdentifier());
            replacementDefault = Optional.of(replacement);
        }
        Account archived = existing.withArchived(true);
        repository.update(archived);
        return new AccountArchiveResult(archived, replacementDefault);
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

    public Account getDefaultAccount() {
        String identifier = preferenceRepository.findDefaultAccountId()
                .orElse(Account.DEFAULT_IDENTIFIER);
        Account account = resolveAccount(identifier);
        if (!account.isActive()) {
            throw new RepositoryException(
                    "The configured default account is archived.");
        }
        return account;
    }

    public Account setDefaultAccount(String identifier) {
        Account account = requireSelectable(resolveAccount(identifier));
        preferenceRepository.saveDefaultAccountId(account.getIdentifier());
        return account;
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

    private static final class MemoryAccountPreferenceRepository
            implements AccountPreferenceRepository {

        private String defaultIdentifier;

        @Override
        public Optional<String> findDefaultAccountId() {
            return Optional.ofNullable(defaultIdentifier);
        }

        @Override
        public void saveDefaultAccountId(String identifier) {
            defaultIdentifier = Objects.requireNonNull(identifier);
        }
    }
}
