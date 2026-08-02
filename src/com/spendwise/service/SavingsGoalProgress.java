package com.spendwise.service;

import com.spendwise.model.GoalContribution;
import com.spendwise.model.SavingsGoal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record SavingsGoalProgress(
        SavingsGoal goal,
        BigDecimal contributedAmount,
        BigDecimal remainingAmount,
        BigDecimal progressPercentage,
        boolean achieved,
        List<GoalContribution> contributions) {

    public SavingsGoalProgress {
        Objects.requireNonNull(goal);
        Objects.requireNonNull(contributedAmount);
        Objects.requireNonNull(remainingAmount);
        Objects.requireNonNull(progressPercentage);
        contributions = List.copyOf(Objects.requireNonNull(contributions));
    }

    public static SavingsGoalProgress from(
            SavingsGoal goal, List<GoalContribution> contributions) {
        BigDecimal current = contributions.stream()
                .map(GoalContribution::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal remaining = goal.getTargetAmount().subtract(current);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO.setScale(2);
        BigDecimal percentage = current.multiply(new BigDecimal("100"))
                .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);
        return new SavingsGoalProgress(goal, current, remaining, percentage,
                current.compareTo(goal.getTargetAmount()) >= 0, contributions);
    }
}
