package com.spendwise.repository;

import com.spendwise.model.MonthlyBudget;
import java.time.YearMonth;
import java.util.Optional;

public interface BudgetRepository {

    Optional<MonthlyBudget> findByMonth(YearMonth month);

    void save(MonthlyBudget budget);

    boolean delete(YearMonth month);
}
