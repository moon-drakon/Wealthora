package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.util.Objects;

public final class Account implements Comparable<Account> {

    public static final String DEFAULT_IDENTIFIER = "ACCOUNT_DEFAULT_CASH";
    public static final Account DEFAULT = new Account(
            DEFAULT_IDENTIFIER,
            "Cash",
            AccountType.CASH,
            new BigDecimal("0.00"),
            true,
            false);

    private static final String CUSTOM_PREFIX = "ACCOUNT_";

    private final String identifier;
    private final String displayName;
    private final AccountType type;
    private final BigDecimal openingBalance;
    private final boolean protectedAccount;
    private final boolean archived;

    private Account(
            String identifier,
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            boolean protectedAccount,
            boolean archived) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Account", CUSTOM_PREFIX);
        this.displayName = FinanceValidator.validateRequiredText(
                displayName, "Account name", FinanceValidator.MAX_NAME_LENGTH);
        this.type = Objects.requireNonNull(type, "Account type is required.");
        this.openingBalance = FinanceValidator.validateSignedAmount(
                openingBalance, "Opening balance");
        this.protectedAccount = protectedAccount;
        this.archived = protectedAccount ? false : archived;
    }

    public static Account createCustom(
            String identifier,
            String displayName,
            AccountType type,
            BigDecimal openingBalance,
            boolean archived) {
        if (DEFAULT_IDENTIFIER.equals(identifier)) {
            throw new ValidationException(
                    "The protected default account identifier is reserved.");
        }
        return new Account(
                identifier,
                displayName,
                type,
                openingBalance,
                false,
                archived);
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountType getType() {
        return type;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public boolean isProtected() {
        return protectedAccount;
    }

    public boolean isActive() {
        return !archived;
    }

    public boolean isArchived() {
        return archived;
    }

    public Account withDisplayName(String newDisplayName) {
        return withMetadata(newDisplayName, type);
    }

    public Account withMetadata(
            String newDisplayName, AccountType newType) {
        if (protectedAccount) {
            throw new ValidationException(
                    "The protected default account metadata cannot be changed.");
        }
        return createCustom(
                identifier,
                newDisplayName,
                Objects.requireNonNull(newType, "Account type is required."),
                openingBalance,
                archived);
    }

    public Account withArchived(boolean newArchived) {
        if (protectedAccount) {
            throw new ValidationException(
                    "The protected default account cannot be archived.");
        }
        return createCustom(
                identifier, displayName, type, openingBalance, newArchived);
    }

    @Override
    public int compareTo(Account other) {
        Objects.requireNonNull(other, "Account to compare is required.");
        if (identifier.equals(other.identifier)) {
            return 0;
        }
        if (protectedAccount != other.protectedAccount) {
            return protectedAccount ? -1 : 1;
        }
        int nameComparison = displayName.compareToIgnoreCase(other.displayName);
        return nameComparison != 0
                ? nameComparison
                : identifier.compareTo(other.identifier);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Account account
                && identifier.equals(account.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
