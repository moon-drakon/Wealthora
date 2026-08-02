package com.spendwise.service;

import java.math.BigDecimal;
import java.util.Objects;

public record RecurringCommitmentSummary(
        BigDecimal scheduledIncome,
        BigDecimal scheduledExpenses,
        BigDecimal scheduledTransfers,
        BigDecimal bills,
        BigDecimal subscriptions) {

    public RecurringCommitmentSummary {
        scheduledIncome = money(scheduledIncome);
        scheduledExpenses = money(scheduledExpenses);
        scheduledTransfers = money(scheduledTransfers);
        bills = money(bills);
        subscriptions = money(subscriptions);
    }

    private static BigDecimal money(BigDecimal value) {
        return Objects.requireNonNull(value).setScale(2);
    }
}
