package com.spendwise.service;

import com.spendwise.model.Expense;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExpenseAnalyticsService {

    private static final int DEFAULT_TREND_MONTH_COUNT = 6;
    private static final int MINIMUM_TREND_MONTH_COUNT = 1;
    private static final int MAXIMUM_TREND_MONTH_COUNT = 12;
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    private final ExpenseService expenseService;

    public ExpenseAnalyticsService(ExpenseService expenseService) {
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
    }

    public ExpenseAnalyticsSnapshot analyzeMonth(YearMonth selectedMonth) {
        return analyzeMonth(selectedMonth, DEFAULT_TREND_MONTH_COUNT);
    }

    public ExpenseAnalyticsSnapshot analyzeMonth(
            YearMonth selectedMonth, int trendMonthCount) {
        if (selectedMonth == null) {
            throw new ValidationException("Selected month is required.");
        }
        if (trendMonthCount < MINIMUM_TREND_MONTH_COUNT
                || trendMonthCount > MAXIMUM_TREND_MONTH_COUNT) {
            throw new ValidationException(
                    "Trend month count must be between 1 and 12.");
        }

        YearMonth previousMonth = selectedMonth.minusMonths(1);
        LinkedHashMap<YearMonth, BigDecimal> monthlyTotals =
                emptyMonthlyTotals(selectedMonth, trendMonthCount);
        List<Expense> selectedMonthExpenses = new ArrayList<>();
        BigDecimal previousMonthTotal = ZERO_AMOUNT;

        List<Expense> allExpenses = expenseService.getAllExpenses();
        for (Expense expense : allExpenses) {
            YearMonth expenseMonth = YearMonth.from(expense.getDate());
            if (expenseMonth.equals(selectedMonth)) {
                selectedMonthExpenses.add(expense);
            }
            if (expenseMonth.equals(previousMonth)) {
                previousMonthTotal = previousMonthTotal.add(expense.getAmount());
            }
            if (monthlyTotals.containsKey(expenseMonth)) {
                monthlyTotals.put(
                        expenseMonth,
                        monthlyTotals.get(expenseMonth).add(expense.getAmount()));
            }
        }

        ExpenseSummary selectedMonthSummary =
                expenseService.calculateSummary(selectedMonthExpenses);
        BigDecimal changeFromPreviousMonth =
                selectedMonthSummary.getTotalAmount().subtract(previousMonthTotal);
        return new ExpenseAnalyticsSnapshot(
                selectedMonth,
                selectedMonthSummary,
                selectedMonthExpenses,
                previousMonthTotal,
                changeFromPreviousMonth,
                monthlyTotals);
    }

    private static LinkedHashMap<YearMonth, BigDecimal> emptyMonthlyTotals(
            YearMonth selectedMonth, int trendMonthCount) {
        LinkedHashMap<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();
        YearMonth firstMonth = selectedMonth.minusMonths(trendMonthCount - 1L);
        for (int monthOffset = 0; monthOffset < trendMonthCount; monthOffset++) {
            monthlyTotals.put(firstMonth.plusMonths(monthOffset), ZERO_AMOUNT);
        }
        return monthlyTotals;
    }
}
