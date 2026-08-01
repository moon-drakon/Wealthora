package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class FinancialActivityEntry {

    private final FinancialEntryType type;
    private final LocalDate date;
    private final BigDecimal amount;
    private final Account account;
    private final Account destinationAccount;
    private final Category category;
    private final String description;

    public FinancialActivityEntry(
            FinancialEntryType type,
            LocalDate date,
            BigDecimal amount,
            Account account,
            Account destinationAccount,
            Category category,
            String description) {
        this.type = Objects.requireNonNull(type, "Activity type is required.");
        this.date = Objects.requireNonNull(date, "Activity date is required.");
        this.amount = Objects.requireNonNull(amount, "Activity amount is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
        this.account = Objects.requireNonNull(account, "Activity account is required.");
        this.destinationAccount = destinationAccount;
        this.category = category;
        this.description = Objects.requireNonNull(
                description, "Activity description is required.");
        if (type == FinancialEntryType.TRANSFER && destinationAccount == null) {
            throw new IllegalArgumentException(
                    "Transfer activity requires a destination account.");
        }
        if (type != FinancialEntryType.TRANSFER && destinationAccount != null) {
            throw new IllegalArgumentException(
                    "Only transfer activity can have a destination account.");
        }
        if (type == FinancialEntryType.EXPENSE && category == null) {
            throw new IllegalArgumentException(
                    "Expense activity requires a category.");
        }
    }

    public FinancialEntryType getType() {
        return type;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Account getAccount() {
        return account;
    }

    public Optional<Account> getDestinationAccount() {
        return Optional.ofNullable(destinationAccount);
    }

    public Optional<Category> getCategory() {
        return Optional.ofNullable(category);
    }

    public String getDescription() {
        return description;
    }
}
