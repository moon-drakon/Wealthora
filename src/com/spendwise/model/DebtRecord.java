package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class DebtRecord {
    private static final String ID_PREFIX = "DEBT_";
    private final String identifier;
    private final DebtDirection direction;
    private final String counterparty;
    private final BigDecimal originalAmount;
    private final LocalDate dueDate;
    private final String note;

    public static DebtRecord create(
            DebtDirection direction, String counterparty,
            BigDecimal originalAmount, LocalDate dueDate, String note) {
        return new DebtRecord(
                ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                        .toUpperCase(Locale.ROOT),
                direction, counterparty, originalAmount, dueDate, note);
    }

    public DebtRecord(
            String identifier, DebtDirection direction, String counterparty,
            BigDecimal originalAmount, LocalDate dueDate, String note) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Debt", ID_PREFIX);
        this.direction = Objects.requireNonNull(
                direction, "Debt direction is required.");
        this.counterparty = FinanceValidator.validateRequiredText(
                counterparty, "Counterparty", FinanceValidator.MAX_NAME_LENGTH);
        this.originalAmount = FinanceValidator.validatePositiveAmount(
                originalAmount, "Original debt amount");
        this.dueDate = Objects.requireNonNull(dueDate, "Debt due date is required.");
        this.note = FinanceValidator.validateOptionalText(
                note, "Debt note", FinanceValidator.MAX_NOTE_LENGTH);
    }

    public String getIdentifier() { return identifier; }
    public DebtDirection getDirection() { return direction; }
    public String getCounterparty() { return counterparty; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public String getNote() { return note; }
}
