package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public final class DebtRepayment {
    private static final String ID_PREFIX = "REPAYMENT_";
    private final String identifier;
    private final String debtIdentifier;
    private final LocalDate date;
    private final BigDecimal amount;
    private final String note;

    public static DebtRepayment create(
            String debtIdentifier, LocalDate date,
            BigDecimal amount, String note) {
        return new DebtRepayment(
                ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                        .toUpperCase(Locale.ROOT),
                debtIdentifier, date, amount, note);
    }

    public DebtRepayment(
            String identifier, String debtIdentifier, LocalDate date,
            BigDecimal amount, String note) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Debt repayment", ID_PREFIX);
        this.debtIdentifier = FinanceValidator.validateIdentifier(
                debtIdentifier, "Debt", "DEBT_");
        this.date = FinanceValidator.validatePostedDate(date, "Repayment date");
        this.amount = FinanceValidator.validatePositiveAmount(
                amount, "Repayment amount");
        this.note = FinanceValidator.validateOptionalText(
                note, "Repayment note", FinanceValidator.MAX_NOTE_LENGTH);
    }

    public String getIdentifier() { return identifier; }
    public String getDebtIdentifier() { return debtIdentifier; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
}
