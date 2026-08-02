package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.GoalContribution;
import com.spendwise.model.SavingsGoal;
import com.spendwise.repository.SavingsGoalRepository;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SavingsGoalService {
    private final SavingsGoalRepository repository;
    private final AccountService accountService;

    public SavingsGoalService(
            SavingsGoalRepository repository, AccountService accountService) {
        this.repository = Objects.requireNonNull(repository);
        this.accountService = Objects.requireNonNull(accountService);
    }

    public List<SavingsGoal> listGoals() {
        return repository.findAllGoals().stream()
                .sorted(java.util.Comparator
                        .comparing(SavingsGoal::isActive).reversed()
                        .thenComparing(SavingsGoal::getTargetDate)
                        .thenComparing(SavingsGoal::getName,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public SavingsGoal addGoal(
            String name, BigDecimal targetAmount, LocalDate targetDate,
            Account linkedAccount) {
        rejectDuplicateName(name, null);
        SavingsGoal goal = SavingsGoal.create(name, targetAmount, targetDate,
                accountService.requireSelectable(linkedAccount));
        repository.addGoal(goal);
        return goal;
    }

    public SavingsGoal updateGoal(
            String identifier, String name, BigDecimal targetAmount,
            LocalDate targetDate, Account linkedAccount) {
        SavingsGoal existing = requireGoal(identifier);
        rejectDuplicateName(name, existing.getIdentifier());
        Account account = accountService.requireSelectableOrHistorical(
                linkedAccount, existing.getLinkedAccount());
        SavingsGoal replacement = new SavingsGoal(existing.getIdentifier(),
                name, targetAmount, targetDate, account, existing.isActive());
        repository.updateGoal(replacement);
        return replacement;
    }

    public SavingsGoal setActive(String identifier, boolean active) {
        SavingsGoal existing = requireGoal(identifier);
        if (active) accountService.requireSelectable(existing.getLinkedAccount());
        SavingsGoal replacement = existing.withActive(active);
        repository.updateGoal(replacement);
        return replacement;
    }

    public GoalContribution addContribution(
            String goalIdentifier, LocalDate date,
            BigDecimal amount, String note) {
        SavingsGoal goal = requireGoal(goalIdentifier);
        if (!goal.isActive()) {
            throw new ValidationException(
                    "Archived savings goals cannot receive contributions.");
        }
        GoalContribution contribution = GoalContribution.create(
                goal.getIdentifier(), date, amount, note);
        repository.addContribution(contribution);
        return contribution;
    }

    public SavingsGoalProgress getProgress(String identifier) {
        SavingsGoal goal = requireGoal(identifier);
        return SavingsGoalProgress.from(goal,
                repository.findContributions(goal.getIdentifier()));
    }

    private SavingsGoal requireGoal(String identifier) {
        String required = FinanceValidator.validateIdentifier(
                identifier, "Savings goal", "GOAL_");
        return repository.findGoalById(required).orElseThrow(() ->
                new FinanceNotFoundException("Savings goal was not found."));
    }

    private void rejectDuplicateName(String name, String ignoredId) {
        String normalized = FinanceValidator.validateRequiredText(
                name, "Savings goal name", FinanceValidator.MAX_NAME_LENGTH)
                .toLowerCase(Locale.ROOT);
        if (repository.findAllGoals().stream()
                .filter(SavingsGoal::isActive)
                .filter(goal -> ignoredId == null
                        || !goal.getIdentifier().equals(ignoredId))
                .anyMatch(goal -> goal.getName().toLowerCase(Locale.ROOT)
                        .equals(normalized))) {
            throw new ValidationException(
                    "An active savings goal with this name already exists.");
        }
    }
}
