package com.spendwise.service;

import com.spendwise.model.Account;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class AccountStatementSnapshot {

    private final Account account;
    private final BigDecimal openingBalance;
    private final BigDecimal income;
    private final BigDecimal expenses;
    private final BigDecimal incomingTransfers;
    private final BigDecimal outgoingTransfers;
    private final BigDecimal currentBalance;
    private final List<FinancialActivityEntry> entries;

    public AccountStatementSnapshot(
            Account account,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal incomingTransfers,
            BigDecimal outgoingTransfers,
            BigDecimal currentBalance,
            List<FinancialActivityEntry> entries) {
        this.account = Objects.requireNonNull(account, "Account is required.");
        this.openingBalance = money(
                account.getOpeningBalance(), "Opening balance");
        this.income = money(income, "Account income");
        this.expenses = money(expenses, "Account expenses");
        this.incomingTransfers = money(
                incomingTransfers, "Incoming transfers");
        this.outgoingTransfers = money(
                outgoingTransfers, "Outgoing transfers");
        this.currentBalance = money(currentBalance, "Current balance");
        this.entries = List.copyOf(Objects.requireNonNull(
                entries, "Statement entries are required."));
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
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

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public List<FinancialActivityEntry> getEntries() {
        return entries;
    }

    private static BigDecimal money(BigDecimal value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
