package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class Income extends Transaction {

    private static final String ID_PREFIX = "INCOME_";

    private final String source;

    public Income(
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            String note) {
        this(ID_PREFIX + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase(Locale.ROOT),
                date, amount, source, account,
                PaymentMethod.UNSPECIFIED, List.of(), note);
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
        super(
                FinanceValidator.validateIdentifier(id, "Income", ID_PREFIX),
                FinanceValidator.validatePositiveAmount(
                        amount, "Income amount"),
                FinanceValidator.validatePostedDate(date, "Income date"),
                Category.INCOME,
                Objects.requireNonNull(account,
                        "Income account is required."),
                Objects.requireNonNull(paymentMethod,
                        "Income payment method is required."),
                FinanceValidator.validateTags(tags),
                FinanceValidator.validateOptionalText(
                        note, "Income note", FinanceValidator.MAX_NOTE_LENGTH));
        this.source = FinanceValidator.validateRequiredText(
                source, "Income source", FinanceValidator.MAX_NAME_LENGTH);
    }

    public String getSource() {
        return source;
    }

    @Override
    public String getDescription() {
        return source;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.INCOME;
    }

    @Override
    public BigDecimal calculateImpact() {
        return getAmount();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Income income
                        && getId().equals(income.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return "Income{"
                + "id='" + getId() + '\''
                + ", date=" + getDate()
                + ", amount=" + getAmount()
                + ", source='" + source + '\''
                + ", account=" + getAccount()
                + '}';
    }
}
