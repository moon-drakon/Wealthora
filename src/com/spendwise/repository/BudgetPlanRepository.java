package com.spendwise.repository;

import com.spendwise.model.BudgetPlan;
import java.util.List;
import java.util.Optional;

public interface BudgetPlanRepository {
    List<BudgetPlan> findAll();
    Optional<BudgetPlan> findById(String identifier);
    void add(BudgetPlan plan);
    void update(BudgetPlan plan);
}
