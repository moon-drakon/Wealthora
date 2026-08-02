package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

public final class Transfer {

    private static final String ID_PREFIX = "TRANSFER_";

    private final String id;
    private final LocalDate date;
    private final BigDecimal amount;
    private final Account sourceAccount;
    private final Account destinationAccount;
    private final String note;
    private final List<String> tags;

    public Transfer(
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            String note) {
        this(
                ID_PREFIX + UUID.randomUUID().toString()
                        .replace("-", "").toUpperCase(Locale.ROOT),
                date,
                amount,
                sourceAccount,
                destinationAccount,
                List.of(),
                note);
    }

    public Transfer(
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            List<String> tags,
            String note) {
        this(ID_PREFIX + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase(Locale.ROOT),
                date, amount, sourceAccount, destinationAccount, tags, note);
    }

    public Transfer(
            String id,
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            String note) {
        this(id, date, amount, sourceAccount, destinationAccount,
                List.of(), note);
    }

    public Transfer(
            String id,
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            List<String> tags,
            String note) {
        this.id = FinanceValidator.validateIdentifier(
                id, "Transfer", ID_PREFIX);
        this.date = FinanceValidator.validatePostedDate(date, "Transfer date");
        this.amount = FinanceValidator.validatePositiveAmount(
                amount, "Transfer amount");
        this.sourceAccount = Objects.requireNonNull(
                sourceAccount, "Source account is required.");
        this.destinationAccount = Objects.requireNonNull(
                destinationAccount, "Destination account is required.");
        if (sourceAccount.equals(destinationAccount)) {
            throw new ValidationException(
                    "Source and destination accounts must be different.");
        }
        this.tags = FinanceValidator.validateTags(tags);
        this.note = FinanceValidator.validateOptionalText(
                note, "Transfer note", FinanceValidator.MAX_NOTE_LENGTH);
    }

    public String getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public String getNote() {
        return note;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Transfer transfer && id.equals(transfer.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Transfer{"
                + "id='" + id + '\''
                + ", date=" + date
                + ", amount=" + amount
                + ", sourceAccount=" + sourceAccount
                + ", destinationAccount=" + destinationAccount
                + '}';
    }
}
