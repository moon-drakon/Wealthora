package com.spendwise.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Objects;

public final class MonthlyCashFlowSummary {

    private final YearMonth month;
    private final BigDecimal income;
    private final BigDecimal expenses;
    private final BigDecimal netCashFlow;

    public MonthlyCashFlowSummary(
            YearMonth month, BigDecimal income, BigDecimal expenses) {
        this.month = Objects.requireNonNull(month, "Trend month is required.");
        this.income = money(income, "Monthly income");
        this.expenses = money(expenses, "Monthly expenses");
        this.netCashFlow = this.income.subtract(this.expenses)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public BigDecimal getExpenses() {
        return expenses;
    }

    public BigDecimal getNetCashFlow() {
        return netCashFlow;
    }

    private static BigDecimal money(BigDecimal value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
