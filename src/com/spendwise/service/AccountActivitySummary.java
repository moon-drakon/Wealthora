package com.spendwise.service;

import com.spendwise.model.Account;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class AccountActivitySummary {

    private final Account account;
    private final BigDecimal income;
    private final BigDecimal expenses;
    private final BigDecimal incomingTransfers;
    private final BigDecimal outgoingTransfers;
    private final BigDecimal netActivity;

    public AccountActivitySummary(
            Account account,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal incomingTransfers,
            BigDecimal outgoingTransfers) {
        this.account = Objects.requireNonNull(account, "Account is required.");
        this.income = money(income, "Account income");
        this.expenses = money(expenses, "Account expenses");
        this.incomingTransfers = money(
                incomingTransfers, "Incoming transfers");
        this.outgoingTransfers = money(
                outgoingTransfers, "Outgoing transfers");
        this.netActivity = this.income
                .subtract(this.expenses)
                .add(this.incomingTransfers)
                .subtract(this.outgoingTransfers)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public BigDecimal getExpenses() {
        return expenses;
    }

    public BigDecimal getIncomingTransfers() {
        return incomingTransfers;
    }

    public BigDecimal getOutgoingTransfers() {
        return outgoingTransfers;
    }

    public BigDecimal getNetActivity() {
        return netActivity;
    }

    private static BigDecimal money(BigDecimal value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
