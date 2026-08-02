package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public final class GoalContribution {
    private static final String ID_PREFIX = "CONTRIBUTION_";

    private final String identifier;
    private final String goalIdentifier;
    private final LocalDate date;
    private final BigDecimal amount;
    private final String note;

    public static GoalContribution create(
            String goalIdentifier, LocalDate date,
            BigDecimal amount, String note) {
        return new GoalContribution(
                ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                        .toUpperCase(Locale.ROOT),
                goalIdentifier, date, amount, note);
    }

    public GoalContribution(
            String identifier, String goalIdentifier, LocalDate date,
            BigDecimal amount, String note) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Goal contribution", ID_PREFIX);
        this.goalIdentifier = FinanceValidator.validateIdentifier(
                goalIdentifier, "Savings goal", "GOAL_");
        this.date = FinanceValidator.validatePostedDate(
                date, "Contribution date");
        this.amount = FinanceValidator.validatePositiveAmount(
                amount, "Contribution amount");
        this.note = FinanceValidator.validateOptionalText(
                note, "Contribution note", FinanceValidator.MAX_NOTE_LENGTH);
    }

    public String getIdentifier() { return identifier; }
    public String getGoalIdentifier() { return goalIdentifier; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
}
