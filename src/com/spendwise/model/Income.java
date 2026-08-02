package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

public final class Income {

    private static final String ID_PREFIX = "INCOME_";

    private final String id;
    private final LocalDate date;
    private final BigDecimal amount;
    private final String source;
    private final Account account;
    private final String note;
    private final PaymentMethod paymentMethod;
    private final List<String> tags;

    public Income(
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            String note) {
        this(
                ID_PREFIX + UUID.randomUUID().toString()
                        .replace("-", "").toUpperCase(Locale.ROOT),
                date,
                amount,
                source,
                account,
                PaymentMethod.UNSPECIFIED,
                List.of(),
                note);
    }

    public Income(
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String note) {
        this(ID_PREFIX + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase(Locale.ROOT),
                date, amount, source, account, paymentMethod, tags, note);
    }

    public Income(
            String id,
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            String note) {
        this(id, date, amount, source, account,
                PaymentMethod.UNSPECIFIED, List.of(), note);
    }

    public Income(
            String id,
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String note) {
        this.id = FinanceValidator.validateIdentifier(
                id, "Income", ID_PREFIX);
        this.date = FinanceValidator.validatePostedDate(date, "Income date");
        this.amount = FinanceValidator.validatePositiveAmount(
                amount, "Income amount");
        this.source = FinanceValidator.validateRequiredText(
                source, "Income source", FinanceValidator.MAX_NAME_LENGTH);
        this.account = Objects.requireNonNull(
                account, "Income account is required.");
        this.paymentMethod = Objects.requireNonNull(
                paymentMethod, "Income payment method is required.");
        this.tags = FinanceValidator.validateTags(tags);
        this.note = FinanceValidator.validateOptionalText(
                note, "Income note", FinanceValidator.MAX_NOTE_LENGTH);
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

    public String getSource() {
        return source;
    }

    public Account getAccount() {
        return account;
    }

    public String getNote() {
        return note;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Income income && id.equals(income.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Income{"
                + "id='" + id + '\''
                + ", date=" + date
                + ", amount=" + amount
                + ", source='" + source + '\''
                + ", account=" + account
                + '}';
    }
}
