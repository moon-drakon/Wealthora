package com.spendwise.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Common state and behavior for money entering or leaving Wealthora.
 */
public abstract class Transaction {

    private final String id;
    private BigDecimal amount;
    private LocalDate date;
    private Category category;
    private Account account;
    private PaymentMethod paymentMethod;
    private List<String> tags;
    private String note;

    protected Transaction(
            String id,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String note) {
        this.id = Objects.requireNonNull(id, "Transaction ID is required.");
        updateTransactionDetails(amount, date, category, account,
                paymentMethod, tags, note);
    }

    public final String getId() {
        return id;
    }

    public final BigDecimal getAmount() {
        return amount;
    }

    public final LocalDate getDate() {
        return date;
    }

    public final Category getCategory() {
        return category;
    }

    public final Account getAccount() {
        return account;
    }

    public final PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public final List<String> getTags() {
        return tags;
    }

    public final String getNote() {
        return note;
    }

    public abstract String getDescription();

    public abstract TransactionType getType();

    /**
     * Returns the signed change to the account balance.
     */
    public abstract BigDecimal calculateImpact();

    protected final void updateTransactionDetails(
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String note) {
        this.amount = Objects.requireNonNull(
                amount, "Transaction amount is required.");
        this.date = Objects.requireNonNull(
                date, "Transaction date is required.");
        this.category = Objects.requireNonNull(
                category, "Transaction category is required.");
        this.account = Objects.requireNonNull(
                account, "Transaction account is required.");
        this.paymentMethod = Objects.requireNonNull(
                paymentMethod, "Transaction payment method is required.");
        this.tags = List.copyOf(Objects.requireNonNull(
                tags, "Transaction tags are required."));
        this.note = Objects.requireNonNull(note, "Transaction note is required.");
    }
}
