package com.spendwise.service;

import com.spendwise.model.BudgetPlan;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.BudgetPlanRepository;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AdvancedBudgetService {
    private final BudgetPlanRepository repository;
    private final ExpenseService expenseService;

    public AdvancedBudgetService(
            BudgetPlanRepository repository, ExpenseService expenseService) {
        this.repository = Objects.requireNonNull(repository);
        this.expenseService = Objects.requireNonNull(expenseService);
    }

    public List<BudgetPlan> listHistory() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(BudgetPlan::getStartDate).reversed()
                        .thenComparing(BudgetPlan::getIdentifier))
                .toList();
    }

    public List<BudgetPlan> listActiveOn(LocalDate date) {
        LocalDate required = Objects.requireNonNull(date);
        return listHistory().stream().filter(BudgetPlan::isActive)
                .filter(plan -> plan.includes(required)).toList();
    }

    public BudgetPlan addPlan(
            String name, LocalDate startDate, LocalDate endDate,
            BigDecimal overallLimit, Map<Category, BigDecimal> categoryLimits,
            BudgetRolloverMode rolloverMode) {
        rejectOverlappingName(name, startDate, endDate, null);
        BudgetPlan plan = BudgetPlan.create(name, startDate, endDate,
                overallLimit, categoryLimits, rolloverMode);
        repository.add(plan);
        return plan;
    }

    public BudgetPlan updatePlan(
            String identifier, String name, LocalDate startDate,
            LocalDate endDate, BigDecimal overallLimit,
            Map<Category, BigDecimal> categoryLimits,
            BudgetRolloverMode rolloverMode) {
        BudgetPlan existing = requirePlan(identifier);
        rejectOverlappingName(name, startDate, endDate, existing.getIdentifier());
        BudgetPlan replacement = new BudgetPlan(existing.getIdentifier(), name,
                startDate, endDate, overallLimit, categoryLimits,
                rolloverMode, existing.isActive());
        repository.update(replacement);
        return replacement;
    }

    public BudgetPlan setActive(String identifier, boolean active) {
        BudgetPlan replacement = requirePlan(identifier).withActive(active);
        repository.update(replacement);
        return replacement;
    }

    public BudgetPlanStatus evaluate(String identifier) {
        BudgetPlan plan = requirePlan(identifier);
        AppliedLimits applied = appliedLimits(plan, new java.util.HashSet<>());
        List<Expense> expenses = expensesFor(plan);
        BigDecimal spent = total(expenses);
        BudgetUsage overall = new BudgetUsage(spent,
                java.util.Optional.ofNullable(applied.overall()));
        LinkedHashMap<Category, BudgetUsage> categories = new LinkedHashMap<>();
        for (Map.Entry<Category, BigDecimal> entry
                : applied.categories().entrySet()) {
            BigDecimal categorySpent = total(expenses.stream()
                    .filter(expense -> expense.getCategory().equals(entry.getKey()))
                    .toList());
            categories.put(entry.getKey(), new BudgetUsage(
                    categorySpent, java.util.Optional.of(entry.getValue())));
        }
        return new BudgetPlanStatus(plan, overall, applied.overallRollover(),
                categories, applied.categoryRollover());
    }

    private AppliedLimits appliedLimits(
            BudgetPlan plan, java.util.Set<String> visited) {
        if (!visited.add(plan.getIdentifier())) {
            throw new IllegalStateException("Budget rollover cycle detected.");
        }
        BigDecimal overall = plan.getOverallLimit().orElse(null);
        LinkedHashMap<Category, BigDecimal> categories =
                new LinkedHashMap<>(plan.getCategoryLimits());
        BigDecimal overallRollover = BigDecimal.ZERO.setScale(2);
        LinkedHashMap<Category, BigDecimal> categoryRollover =
                new LinkedHashMap<>();
        if (plan.getRolloverMode() == BudgetRolloverMode.CARRY_UNUSED) {
            BudgetPlan previous = previousPlan(plan);
            if (previous != null) {
                AppliedLimits previousLimits = appliedLimits(previous, visited);
                List<Expense> previousExpenses = expensesFor(previous);
                if (overall != null && previousLimits.overall() != null) {
                    overallRollover = positiveRemainder(
                            previousLimits.overall(), total(previousExpenses));
                    overall = overall.add(overallRollover);
                }
                for (Map.Entry<Category, BigDecimal> entry
                        : new ArrayList<>(categories.entrySet())) {
                    BigDecimal priorLimit = previousLimits.categories()
                            .get(entry.getKey());
                    if (priorLimit == null) {
                        continue;
                    }
                    BigDecimal previousSpent = total(previousExpenses.stream()
                            .filter(expense -> expense.getCategory()
                                    .equals(entry.getKey()))
                            .toList());
                    BigDecimal rollover = positiveRemainder(
                            priorLimit, previousSpent);
                    categories.put(entry.getKey(), entry.getValue().add(rollover));
                    categoryRollover.put(entry.getKey(), rollover);
                }
            }
        }
        return new AppliedLimits(overall, categories, overallRollover,
                categoryRollover);
    }

    private BudgetPlan previousPlan(BudgetPlan plan) {
        String name = plan.getName().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(candidate -> !candidate.getIdentifier()
                        .equals(plan.getIdentifier()))
                .filter(candidate -> candidate.getName()
                        .toLowerCase(Locale.ROOT).equals(name))
                .filter(candidate -> candidate.getEndDate()
                        .isBefore(plan.getStartDate()))
                .max(Comparator.comparing(BudgetPlan::getEndDate))
                .orElse(null);
    }

    private List<Expense> expensesFor(BudgetPlan plan) {
        return expenseService.getAllExpenses().stream()
                .filter(expense -> plan.includes(expense.getDate())).toList();
    }

    private static BigDecimal total(List<Expense> expenses) {
        return expenses.stream().map(Expense::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private BudgetPlan requirePlan(String identifier) {
        String required = FinanceValidator.validateIdentifier(
                identifier, "Budget plan", "BUDGET_");
        return repository.findById(required).orElseThrow(() ->
                new FinanceNotFoundException("Budget plan was not found."));
    }

    private void rejectOverlappingName(
            String name, LocalDate start, LocalDate end, String ignoredId) {
        String normalized = FinanceValidator.validateRequiredText(
                name, "Budget name", FinanceValidator.MAX_NAME_LENGTH)
                .toLowerCase(Locale.ROOT);
        LocalDate requiredStart = Objects.requireNonNull(start);
        LocalDate requiredEnd = Objects.requireNonNull(end);
        if (requiredEnd.isBefore(requiredStart)) {
            throw new ValidationException(
                    "Budget end date must not be before its start date.");
        }
        boolean overlaps = repository.findAll().stream()
                .filter(BudgetPlan::isActive)
                .filter(plan -> ignoredId == null
                        || !plan.getIdentifier().equals(ignoredId))
                .filter(plan -> plan.getName().toLowerCase(Locale.ROOT)
                        .equals(normalized))
                .anyMatch(plan -> !plan.getEndDate().isBefore(requiredStart)
                        && !plan.getStartDate().isAfter(requiredEnd));
        if (overlaps) {
            throw new ValidationException(
                    "An active budget with this name overlaps the selected period.");
        }
    }

    private static BigDecimal positiveRemainder(
            BigDecimal limit, BigDecimal spent) {
        BigDecimal remaining = limit.subtract(spent);
        return remaining.signum() > 0 ? remaining
                : BigDecimal.ZERO.setScale(2);
    }

    private record AppliedLimits(
            BigDecimal overall, Map<Category, BigDecimal> categories,
            BigDecimal overallRollover,
            Map<Category, BigDecimal> categoryRollover) {
    }
}
