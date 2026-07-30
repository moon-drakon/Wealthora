package com.spendwise.service;

import com.spendwise.model.Expense;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExpenseAnalyticsSnapshot {

    private static final int MONEY_SCALE = 2;

    private final YearMonth selectedMonth;
    private final ExpenseSummary selectedMonthSummary;
    private final List<Expense> selectedMonthExpenses;
    private final BigDecimal previousMonthTotal;
    private final BigDecimal changeFromPreviousMonth;
    private final Map<YearMonth, BigDecimal> monthlyTotals;

    public ExpenseAnalyticsSnapshot(
            YearMonth selectedMonth,
            ExpenseSummary selectedMonthSummary,
            List<Expense> selectedMonthExpenses,
            BigDecimal previousMonthTotal,
            BigDecimal changeFromPreviousMonth,
            Map<YearMonth, BigDecimal> monthlyTotals) {
        this.selectedMonth = Objects.requireNonNull(
                selectedMonth, "Selected month is required.");
        this.selectedMonthSummary = Objects.requireNonNull(
                selectedMonthSummary, "Selected-month summary is required.");
        this.selectedMonthExpenses = copyExpenses(selectedMonthExpenses);
        this.previousMonthTotal = normalizeMoney(
                previousMonthTotal, "Previous-month total");
        this.changeFromPreviousMonth = normalizeMoney(
                changeFromPreviousMonth, "Change from previous month");
        this.monthlyTotals = copyMonthlyTotals(monthlyTotals);
    }

    public YearMonth getSelectedMonth() {
        return selectedMonth;
    }

    public ExpenseSummary getSelectedMonthSummary() {
        return selectedMonthSummary;
    }

    public List<Expense> getSelectedMonthExpenses() {
        return selectedMonthExpenses;
    }

    public BigDecimal getPreviousMonthTotal() {
        return previousMonthTotal;
    }

    public BigDecimal getChangeFromPreviousMonth() {
        return changeFromPreviousMonth;
    }

    public Map<YearMonth, BigDecimal> getMonthlyTotals() {
        return monthlyTotals;
    }

    private static List<Expense> copyExpenses(List<Expense> expenses) {
        Objects.requireNonNull(expenses, "Selected-month expenses are required.");
        List<Expense> copiedExpenses = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) {
            copiedExpenses.add(Objects.requireNonNull(
                    expense, "Selected-month expenses cannot contain null elements."));
        }
        return List.copyOf(copiedExpenses);
    }

    private static Map<YearMonth, BigDecimal> copyMonthlyTotals(
            Map<YearMonth, BigDecimal> monthlyTotals) {
        Objects.requireNonNull(monthlyTotals, "Monthly totals are required.");
        LinkedHashMap<YearMonth, BigDecimal> copiedTotals = new LinkedHashMap<>();
        YearMonth previousMonth = null;
        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyTotals.entrySet()) {
            YearMonth month = Objects.requireNonNull(
                    entry.getKey(), "Monthly totals cannot contain null month keys.");
            if (previousMonth != null && !month.isAfter(previousMonth)) {
                throw new IllegalArgumentException(
                        "Monthly totals must be in chronological order.");
            }
            copiedTotals.put(
                    month,
                    normalizeMoney(
                            entry.getValue(), "Monthly total for " + month));
            previousMonth = month;
        }
        return Collections.unmodifiableMap(copiedTotals);
    }

    private static BigDecimal normalizeMoney(BigDecimal amount, String fieldName) {
        return Objects.requireNonNull(amount, fieldName + " is required.")
                .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
