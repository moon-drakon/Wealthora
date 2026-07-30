package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Objects;

public final class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = Objects.requireNonNull(
                budgetRepository, "Budget repository is required.");
    }

    public MonthlyBudget getBudget(YearMonth month) {
        YearMonth requiredMonth = Objects.requireNonNull(
                month, "Budget month is required.");
        return budgetRepository.findByMonth(requiredMonth)
                .orElseGet(() -> MonthlyBudget.empty(requiredMonth));
    }

    public void saveBudget(MonthlyBudget budget) {
        MonthlyBudget requiredBudget = Objects.requireNonNull(
                budget, "Monthly budget is required.");
        if (!requiredBudget.hasAnyLimit()) {
            throw new ValidationException(
                    "Enter at least one limit, or use Clear Budget.");
        }
        budgetRepository.save(requiredBudget);
    }

    public boolean clearBudget(YearMonth month) {
        return budgetRepository.delete(
                Objects.requireNonNull(month, "Budget month is required."));
    }

    public BudgetStatusSnapshot evaluate(
            ExpenseAnalyticsSnapshot analyticsSnapshot) {
        ExpenseAnalyticsSnapshot requiredSnapshot = Objects.requireNonNull(
                analyticsSnapshot, "Expense analytics snapshot is required.");
        MonthlyBudget budget = getBudget(requiredSnapshot.getSelectedMonth());
        ExpenseSummary summary = requiredSnapshot.getSelectedMonthSummary();

        BudgetUsage overallUsage = new BudgetUsage(
                summary.getTotalAmount(), budget.getOverallLimit());
        EnumMap<Category, BudgetUsage> categoryUsage =
                new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            categoryUsage.put(
                    category,
                    new BudgetUsage(
                            summary.getTotalForCategory(category),
                            budget.getCategoryLimit(category)));
        }
        return new BudgetStatusSnapshot(
                requiredSnapshot.getSelectedMonth(),
                overallUsage,
                categoryUsage);
    }
}
