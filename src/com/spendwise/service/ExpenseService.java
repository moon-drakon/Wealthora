package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.validation.ExpenseValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ExpenseService {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Expense repository is required.");
    }

    public List<Expense> getAllExpenses() {
        return List.copyOf(repository.findAll());
    }

    public Optional<Expense> findExpenseById(String id) {
        String normalizedId = ExpenseValidator.validateId(id);
        return repository.findById(normalizedId);
    }

    public Expense createExpense(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        Expense expense = new Expense(description, amount, date, category, notes);
        repository.add(expense);
        return expense;
    }

    public Expense updateExpense(
            String id,
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        String normalizedId = ExpenseValidator.validateId(id);
        if (repository.findById(normalizedId).isEmpty()) {
            throw new ExpenseNotFoundException(
                    "Expense with ID '" + normalizedId + "' was not found.");
        }

        Expense replacement = new Expense(
                normalizedId, description, amount, date, category, notes);
        repository.update(replacement);
        return replacement;
    }

    public boolean deleteExpense(String id) {
        String normalizedId = ExpenseValidator.validateId(id);
        return repository.deleteById(normalizedId);
    }

    public List<Expense> findExpenses(
            String searchText,
            Category category,
            LocalDate startDate,
            LocalDate endDate,
            ExpenseSortOrder sortOrder) {
        if (sortOrder == null) {
            throw new ValidationException("Sort order is required.");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ValidationException("Start date must not be after end date.");
        }

        String normalizedSearchText = normalizeSearchText(searchText);
        List<Expense> matches = new ArrayList<>();
        List<Expense> snapshot = repository.findAll();
        for (Expense expense : snapshot) {
            if (matchesSearch(expense, normalizedSearchText)
                    && matchesCategory(expense, category)
                    && matchesDateRange(expense, startDate, endDate)) {
                matches.add(expense);
            }
        }

        if (sortOrder != ExpenseSortOrder.ORIGINAL_ORDER) {
            matches.sort(comparatorFor(sortOrder));
        }
        return List.copyOf(matches);
    }

    public ExpenseSummary calculateSummary(List<Expense> expenses) {
        if (expenses == null) {
            throw new ValidationException("Expense list is required.");
        }

        BigDecimal totalAmount = ZERO_AMOUNT;
        Map<Category, BigDecimal> totalsByCategory = emptyCategoryTotals();
        for (Expense expense : expenses) {
            if (expense == null) {
                throw new ValidationException("Expense list cannot contain null elements.");
            }
            totalAmount = totalAmount.add(expense.getAmount());
            Category category = expense.getCategory();
            totalsByCategory.put(
                    category,
                    totalsByCategory.get(category).add(expense.getAmount()));
        }

        BigDecimal averageAmount = expenses.isEmpty()
                ? ZERO_AMOUNT
                : totalAmount.divide(
                        BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
        return new ExpenseSummary(
                expenses.size(), totalAmount, averageAmount, totalsByCategory);
    }

    public ExpenseSummary calculateOverallSummary() {
        return calculateSummary(repository.findAll());
    }

    private static String normalizeSearchText(String searchText) {
        return searchText == null
                ? ""
                : searchText.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSearch(Expense expense, String searchText) {
        if (searchText.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(expense.getDescription(), searchText)
                || containsIgnoreCase(expense.getNotes(), searchText)
                || containsIgnoreCase(expense.getCategory().name(), searchText)
                || containsIgnoreCase(expense.getCategory().getDisplayName(), searchText);
    }

    private static boolean containsIgnoreCase(String value, String normalizedSearchText) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedSearchText);
    }

    private static boolean matchesCategory(Expense expense, Category category) {
        return category == null || expense.getCategory() == category;
    }

    private static boolean matchesDateRange(
            Expense expense, LocalDate startDate, LocalDate endDate) {
        return (startDate == null || !expense.getDate().isBefore(startDate))
                && (endDate == null || !expense.getDate().isAfter(endDate));
    }

    private static Comparator<Expense> comparatorFor(ExpenseSortOrder sortOrder) {
        return switch (sortOrder) {
            case DATE_NEWEST_FIRST ->
                Comparator.comparing(Expense::getDate).reversed();
            case DATE_OLDEST_FIRST ->
                Comparator.comparing(Expense::getDate);
            case AMOUNT_HIGHEST_FIRST ->
                (first, second) -> second.getAmount().compareTo(first.getAmount());
            case AMOUNT_LOWEST_FIRST ->
                (first, second) -> first.getAmount().compareTo(second.getAmount());
            case DESCRIPTION_A_TO_Z ->
                Comparator.comparing(
                        expense -> expense.getDescription().toLowerCase(Locale.ROOT));
            case DESCRIPTION_Z_TO_A ->
                Comparator.comparing(
                        (Expense expense) ->
                            expense.getDescription().toLowerCase(Locale.ROOT))
                        .reversed();
            case ORIGINAL_ORDER ->
                throw new IllegalArgumentException(
                        "Original order does not require a comparator.");
        };
    }

    private static Map<Category, BigDecimal> emptyCategoryTotals() {
        EnumMap<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            totals.put(category, ZERO_AMOUNT);
        }
        return totals;
    }
}
