package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdvancedReportSnapshot {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpenses;
    private final BigDecimal netCashFlow;
    private final Map<Category, BigDecimal> expensesByCategory;
    private final Map<String, BigDecimal> incomeBySource;
    private final Map<Account, AccountActivitySummary> accountActivity;
    private final Map<YearMonth, MonthlyCashFlowSummary> monthlyTrend;
    private final List<Category> highestExpenseCategories;
    private final List<BudgetActualSummary> budgetActuals;

    public AdvancedReportSnapshot(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            Map<Category, BigDecimal> expensesByCategory,
            Map<String, BigDecimal> incomeBySource,
            Map<Account, AccountActivitySummary> accountActivity,
            Map<YearMonth, MonthlyCashFlowSummary> monthlyTrend,
            List<Category> highestExpenseCategories,
            List<BudgetActualSummary> budgetActuals) {
        this.startDate = Objects.requireNonNull(
                startDate, "Report start date is required.");
        this.endDate = Objects.requireNonNull(
                endDate, "Report end date is required.");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Report start date must not be after end date.");
        }
        this.totalIncome = money(totalIncome, "Report income");
        this.totalExpenses = money(totalExpenses, "Report expenses");
        this.netCashFlow = this.totalIncome.subtract(this.totalExpenses)
                .setScale(2, RoundingMode.UNNECESSARY);
        this.expensesByCategory = copyCategoryTotals(expensesByCategory);
        this.incomeBySource = copySourceTotals(incomeBySource);
        this.accountActivity = copyAccountActivity(accountActivity);
        this.monthlyTrend = copyMonthlyTrend(monthlyTrend);
        this.highestExpenseCategories = List.copyOf(Objects.requireNonNull(
                highestExpenseCategories,
                "Highest expense categories are required."));
        this.budgetActuals = List.copyOf(Objects.requireNonNull(
                budgetActuals, "Budget actual summaries are required."));
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public BigDecimal getNetCashFlow() {
        return netCashFlow;
    }

    public Map<Category, BigDecimal> getExpensesByCategory() {
        return expensesByCategory;
    }

    public Map<String, BigDecimal> getIncomeBySource() {
        return incomeBySource;
    }

    public Map<Account, AccountActivitySummary> getAccountActivity() {
        return accountActivity;
    }

    public Map<YearMonth, MonthlyCashFlowSummary> getMonthlyTrend() {
        return monthlyTrend;
    }

    public List<Category> getHighestExpenseCategories() {
        return highestExpenseCategories;
    }

    public List<BudgetActualSummary> getBudgetActuals() {
        return budgetActuals;
    }

    private static Map<Category, BigDecimal> copyCategoryTotals(
            Map<Category, BigDecimal> values) {
        Objects.requireNonNull(values, "Expense category totals are required.");
        LinkedHashMap<Category, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<Category, BigDecimal> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(),
                            "Expense category cannot be null."),
                    money(entry.getValue(), "Expense category total"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, BigDecimal> copySourceTotals(
            Map<String, BigDecimal> values) {
        Objects.requireNonNull(values, "Income source totals are required.");
        LinkedHashMap<String, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(),
                            "Income source cannot be null."),
                    money(entry.getValue(), "Income source total"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Account, AccountActivitySummary> copyAccountActivity(
            Map<Account, AccountActivitySummary> values) {
        Objects.requireNonNull(values, "Account activity is required.");
        LinkedHashMap<Account, AccountActivitySummary> copy =
                new LinkedHashMap<>();
        for (Map.Entry<Account, AccountActivitySummary> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(),
                            "Account activity cannot contain a null account."),
                    Objects.requireNonNull(entry.getValue(),
                            "Account activity cannot contain a null summary."));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<YearMonth, MonthlyCashFlowSummary> copyMonthlyTrend(
            Map<YearMonth, MonthlyCashFlowSummary> values) {
        Objects.requireNonNull(values, "Monthly trend is required.");
        LinkedHashMap<YearMonth, MonthlyCashFlowSummary> copy =
                new LinkedHashMap<>();
        for (Map.Entry<YearMonth, MonthlyCashFlowSummary> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(),
                            "Monthly trend cannot contain a null month."),
                    Objects.requireNonNull(entry.getValue(),
                            "Monthly trend cannot contain a null summary."));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static BigDecimal money(BigDecimal value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required.")
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
