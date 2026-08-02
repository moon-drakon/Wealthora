package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Objects;
import java.util.List;

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

    public boolean isCategoryReferenced(Category category) {
        return budgetRepository.isCategoryReferenced(
                Objects.requireNonNull(category, "Budget category is required."));
    }

    public List<MonthlyBudget> listBudgetHistory() {
        return budgetRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(
                        MonthlyBudget::getMonth).reversed())
                .toList();
    }

    public BudgetStatusSnapshot evaluate(
            ExpenseAnalyticsSnapshot analyticsSnapshot) {
        ExpenseAnalyticsSnapshot requiredSnapshot = Objects.requireNonNull(
                analyticsSnapshot, "Expense analytics snapshot is required.");
        MonthlyBudget budget = getBudget(requiredSnapshot.getSelectedMonth());
        ExpenseSummary summary = requiredSnapshot.getSelectedMonthSummary();

        BudgetUsage overallUsage = new BudgetUsage(
                summary.getTotalAmount(), budget.getOverallLimit());
        LinkedHashMap<Category, BudgetUsage> categoryUsage = new LinkedHashMap<>();
        Set<Category> categories = new LinkedHashSet<>();
        categories.addAll(java.util.List.of(Category.values()));
        categories.addAll(summary.getTotalsByCategory().keySet());
        categories.addAll(budget.getCategoryLimits().keySet());
        for (Category category : categories) {
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
