package com.spendwise.repository;

import com.spendwise.model.GoalContribution;
import com.spendwise.model.SavingsGoal;
import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository {
    List<SavingsGoal> findAllGoals();
    Optional<SavingsGoal> findGoalById(String identifier);
    List<GoalContribution> findContributions(String goalIdentifier);
    void addGoal(SavingsGoal goal);
    void updateGoal(SavingsGoal goal);
    void addContribution(GoalContribution contribution);
}
