package com.spendwise.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class DailyActivitySnapshot {

    private final LocalDate date;
    private final BigDecimal expenseTotal;
    private final BigDecimal incomeTotal;
    private final BigDecimal netCashFlow;
    private final List<FinancialActivityEntry> entries;

    public DailyActivitySnapshot(
            LocalDate date,
            BigDecimal expenseTotal,
            BigDecimal incomeTotal,
            List<FinancialActivityEntry> entries) {
        this.date = Objects.requireNonNull(date, "Activity date is required.");
        this.expenseTotal = money(expenseTotal, "Daily expense total");
        this.incomeTotal = money(incomeTotal, "Daily income total");
        this.netCashFlow = this.incomeTotal.subtract(this.expenseTotal)
                .setScale(2, RoundingMode.UNNECESSARY);
        this.entries = List.copyOf(Objects.requireNonNull(
                entries, "Daily activity entries are required."));
        for (FinancialActivityEntry entry : this.entries) {
            Objects.requireNonNull(
                    entry, "Daily activity entries cannot contain null values.");
            if (!entry.getDate().equals(date)) {
                throw new IllegalArgumentException(
                        "Daily activity entries must match the snapshot date.");
            }
        }
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getExpenseTotal() {
        return expenseTotal;
    }

    public BigDecimal getIncomeTotal() {
        return incomeTotal;
    }

    public BigDecimal getNetCashFlow() {
        return netCashFlow;
    }

    public List<FinancialActivityEntry> getEntries() {
        return entries;
    }

    public boolean hasActivity() {
        return !entries.isEmpty();
    }

    private static BigDecimal money(BigDecimal value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
