package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class SavingsGoal {
    private static final String ID_PREFIX = "GOAL_";

    private final String identifier;
    private final String name;
    private final BigDecimal targetAmount;
    private final LocalDate targetDate;
    private final Account linkedAccount;
    private final boolean active;

    public static SavingsGoal create(
            String name, BigDecimal targetAmount, LocalDate targetDate,
            Account linkedAccount) {
        return new SavingsGoal(
                ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                        .toUpperCase(Locale.ROOT),
                name, targetAmount, targetDate, linkedAccount, true);
    }

    public SavingsGoal(
            String identifier, String name, BigDecimal targetAmount,
            LocalDate targetDate, Account linkedAccount, boolean active) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Savings goal", ID_PREFIX);
        this.name = FinanceValidator.validateRequiredText(
                name, "Savings goal name", FinanceValidator.MAX_NAME_LENGTH);
        this.targetAmount = FinanceValidator.validatePositiveAmount(
                targetAmount, "Savings target amount");
        this.targetDate = Objects.requireNonNull(
                targetDate, "Savings target date is required.");
        this.linkedAccount = Objects.requireNonNull(
                linkedAccount, "Linked savings account is required.");
        this.active = active;
    }

    public String getIdentifier() { return identifier; }
    public String getName() { return name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public LocalDate getTargetDate() { return targetDate; }
    public Account getLinkedAccount() { return linkedAccount; }
    public boolean isActive() { return active; }

    public SavingsGoal withActive(boolean newActive) {
        return new SavingsGoal(identifier, name, targetAmount, targetDate,
                linkedAccount, newActive);
    }
}
