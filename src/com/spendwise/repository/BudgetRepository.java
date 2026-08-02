package com.spendwise.repository;

import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository {

    Optional<MonthlyBudget> findByMonth(YearMonth month);

    void save(MonthlyBudget budget);

    boolean delete(YearMonth month);

    default List<MonthlyBudget> findAll() {
        return List.of();
    }

    default boolean isCategoryReferenced(Category category) {
        return false;
    }
}
