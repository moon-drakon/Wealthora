package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.BudgetPlan;
import com.spendwise.model.Category;
import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import com.spendwise.model.Expense;
import com.spendwise.model.GoalContribution;
import com.spendwise.model.Income;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.SavingsGoal;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Copies one workspace into another, adding records that are missing and
 * skipping records that are already there.
 *
 * <p>The engine never deletes and never overwrites. A record is treated as a
 * duplicate when its stable identifier already exists, or when a conservative
 * combination of its own values already matches a stored record. Anything that
 * cannot be written is reported rather than dropped silently.
 *
 * <p>Running with {@code dryRun} set produces the same counts without writing,
 * which is what the import preview shows.
 */
final class WorkspaceTransfer {

    /** Separates fingerprint parts so values cannot run together. */
    private static final char SEPARATOR = '\u001F';

    private final FinanceWorkspace source;
    private final FinanceWorkspace target;
    private final boolean dryRun;

    private final LinkedHashMap<String, Integer> imported = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> skipped = new LinkedHashMap<>();
    private final List<ImportIssue> failures = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private final Map<String, Category> categoryMapping = new LinkedHashMap<>();
    private final Map<String, Account> accountMapping = new LinkedHashMap<>();
    private final List<Category> targetCategories = new ArrayList<>();
    private final List<Account> targetAccounts = new ArrayList<>();

    /** Transactions written during this run, so a fatal failure can undo them. */
    private final List<Runnable> compensations = new ArrayList<>();

    WorkspaceTransfer(
            FinanceWorkspace source, FinanceWorkspace target, boolean dryRun) {
        this.source = Objects.requireNonNull(source, "Source workspace is required.");
        this.target = Objects.requireNonNull(target, "Target workspace is required.");
        this.dryRun = dryRun;
    }

    void run() {
        targetCategories.addAll(target.categories().findAll());
        targetAccounts.addAll(target.accounts().findAll());
        copyCategories();
        copyAccounts();
        copyExpenses();
        copyIncome();
        copyTransfers();
        copyMonthlyBudgets();
        copyBudgetPlans();
        copyRecurringEntries();
        copySavingsGoals();
        copyDebts();
    }

    /**
     * Best-effort removal of the transactions written so far. Used only when an
     * import fails in a way that would otherwise leave a half-written ledger.
     */
    void compensate() {
        for (int index = compensations.size() - 1; index >= 0; index--) {
            try {
                compensations.get(index).run();
            } catch (RuntimeException ignored) {
                // A failed rollback step must not hide the original failure.
            }
        }
        compensations.clear();
    }

    Map<String, Integer> imported() {
        return Map.copyOf(imported);
    }

    Map<String, Integer> skipped() {
        return Map.copyOf(skipped);
    }

    List<ImportIssue> failures() {
        return List.copyOf(failures);
    }

    List<String> warnings() {
        return List.copyOf(warnings);
    }

    // ---------------------------------------------------------------- lookups

    /**
     * Finds the equivalent category in the target workspace, creating it when it
     * is genuinely new. Built-in categories always exist on both sides.
     */
    private Category mapCategory(Category incoming) {
        if (incoming == null) {
            return null;
        }
        Category cached = categoryMapping.get(incoming.getIdentifier());
        if (cached != null) {
            return cached;
        }
        if (incoming.isBuiltIn()) {
            categoryMapping.put(incoming.getIdentifier(), incoming);
            return incoming;
        }
        Optional<Category> existing = targetCategories.stream()
                .filter(candidate -> candidate.getIdentifier()
                        .equals(incoming.getIdentifier())
                        || candidate.getDisplayName().equalsIgnoreCase(
                                incoming.getDisplayName()))
                .findFirst();
        if (existing.isPresent()) {
            categoryMapping.put(incoming.getIdentifier(), existing.get());
            count(skipped, WorkspaceSections.CATEGORIES);
            return existing.get();
        }
        Category parent = incoming.getParentIdentifier()
                .map(this::mapCategoryByIdentifier).orElse(null);
        Category created = parent == null || parent.getIdentifier()
                .equals(incoming.getParentIdentifier().orElse(null))
                ? incoming
                : Category.createSubcategory(incoming.getIdentifier(),
                        incoming.getDisplayName(), parent.getIdentifier(),
                        incoming.isArchived());
        if (!dryRun) {
            target.categories().add(created);
        }
        targetCategories.add(created);
        categoryMapping.put(incoming.getIdentifier(), created);
        count(imported, WorkspaceSections.CATEGORIES);
        return created;
    }

    private Category mapCategoryByIdentifier(String identifier) {
        return source.categories().findAll().stream()
                .filter(candidate -> candidate.getIdentifier().equals(identifier))
                .findFirst()
                .map(this::mapCategory)
                .orElse(null);
    }

    /**
     * Finds the equivalent account in the target workspace, creating it when it
     * is genuinely new. The protected default cash account always exists.
     */
    private Account mapAccount(Account incoming) {
        if (incoming == null) {
            return null;
        }
        if (Account.DEFAULT_IDENTIFIER.equals(incoming.getIdentifier())) {
            return Account.DEFAULT;
        }
        Account cached = accountMapping.get(incoming.getIdentifier());
        if (cached != null) {
            return cached;
        }
        Optional<Account> existing = targetAccounts.stream()
                .filter(candidate -> candidate.getIdentifier()
                        .equals(incoming.getIdentifier())
                        || candidate.getDisplayName().equalsIgnoreCase(
                                incoming.getDisplayName()))
                .findFirst();
        if (existing.isPresent()) {
            accountMapping.put(incoming.getIdentifier(), existing.get());
            count(skipped, WorkspaceSections.ACCOUNTS);
            return existing.get();
        }
        if (!dryRun) {
            target.accounts().add(incoming);
        }
        targetAccounts.add(incoming);
        accountMapping.put(incoming.getIdentifier(), incoming);
        count(imported, WorkspaceSections.ACCOUNTS);
        return incoming;
    }

    // ----------------------------------------------------------------- copying

    private void copyCategories() {
        for (Category category : source.categories().findAll()) {
            attempt(WorkspaceSections.CATEGORIES, category.getIdentifier(),
                    () -> mapCategory(category));
        }
    }

    private void copyAccounts() {
        for (Account account : source.accounts().findAll()) {
            attempt(WorkspaceSections.ACCOUNTS, account.getIdentifier(),
                    () -> mapAccount(account));
        }
        source.accountPreference().findDefaultAccountId().ifPresent(identifier -> {
            if (dryRun || Account.DEFAULT_IDENTIFIER.equals(identifier)) {
                return;
            }
            Account mapped = accountMapping.get(identifier);
            if (mapped == null) {
                return;
            }
            attempt(WorkspaceSections.ACCOUNTS, identifier, () ->
                    target.accountPreference().saveDefaultAccountId(
                            mapped.getIdentifier()));
        });
    }

    private void copyExpenses() {
        List<Expense> existing = target.expenses().findAll();
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (Expense expense : existing) {
            identifiers.add(expense.getId());
            fingerprints.add(expenseFingerprint(expense));
        }
        for (Expense expense : source.expenses().findAll()) {
            attempt(WorkspaceSections.EXPENSES, expense.getId(), () -> {
                Category category = mapCategory(expense.getCategory());
                Account account = mapAccount(expense.getAccount());
                Expense mapped = new Expense(expense.getId(),
                        expense.getDescription(), expense.getAmount(),
                        expense.getDate(), category, account,
                        expense.getPaymentMethod(), expense.getTags(),
                        expense.getNotes());
                String fingerprint = expenseFingerprint(mapped);
                if (identifiers.contains(mapped.getId())
                        || fingerprints.contains(fingerprint)) {
                    count(skipped, WorkspaceSections.EXPENSES);
                    return;
                }
                if (!dryRun) {
                    target.expenses().add(mapped);
                    compensations.add(() ->
                            target.expenses().deleteById(mapped.getId()));
                }
                identifiers.add(mapped.getId());
                fingerprints.add(fingerprint);
                count(imported, WorkspaceSections.EXPENSES);
            });
        }
    }

    private void copyIncome() {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (Income entry : target.income().findAll()) {
            identifiers.add(entry.getId());
            fingerprints.add(incomeFingerprint(entry));
        }
        for (Income entry : source.income().findAll()) {
            attempt(WorkspaceSections.INCOME, entry.getId(), () -> {
                Account account = mapAccount(entry.getAccount());
                Income mapped = new Income(entry.getId(), entry.getDate(),
                        entry.getAmount(), entry.getSource(), account,
                        entry.getPaymentMethod(), entry.getTags(),
                        entry.getNote());
                String fingerprint = incomeFingerprint(mapped);
                if (identifiers.contains(mapped.getId())
                        || fingerprints.contains(fingerprint)) {
                    count(skipped, WorkspaceSections.INCOME);
                    return;
                }
                if (!dryRun) {
                    target.income().add(mapped);
                    compensations.add(() ->
                            target.income().deleteById(mapped.getId()));
                }
                identifiers.add(mapped.getId());
                fingerprints.add(fingerprint);
                count(imported, WorkspaceSections.INCOME);
            });
        }
    }

    private void copyTransfers() {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (Transfer entry : target.transfers().findAll()) {
            identifiers.add(entry.getId());
            fingerprints.add(transferFingerprint(entry));
        }
        for (Transfer entry : source.transfers().findAll()) {
            attempt(WorkspaceSections.TRANSFERS, entry.getId(), () -> {
                Account from = mapAccount(entry.getSourceAccount());
                Account to = mapAccount(entry.getDestinationAccount());
                Transfer mapped = new Transfer(entry.getId(), entry.getDate(),
                        entry.getAmount(), from, to, entry.getTags(),
                        entry.getNote());
                String fingerprint = transferFingerprint(mapped);
                if (identifiers.contains(mapped.getId())
                        || fingerprints.contains(fingerprint)) {
                    count(skipped, WorkspaceSections.TRANSFERS);
                    return;
                }
                if (!dryRun) {
                    target.transfers().add(mapped);
                    compensations.add(() ->
                            target.transfers().deleteById(mapped.getId()));
                }
                identifiers.add(mapped.getId());
                fingerprints.add(fingerprint);
                count(imported, WorkspaceSections.TRANSFERS);
            });
        }
    }

    private void copyMonthlyBudgets() {
        for (MonthlyBudget budget : source.budgets().findAll()) {
            YearMonth month = budget.getMonth();
            attempt(WorkspaceSections.MONTHLY_BUDGETS, month.toString(), () -> {
                if (target.budgets().findByMonth(month).isPresent()) {
                    count(skipped, WorkspaceSections.MONTHLY_BUDGETS);
                    return;
                }
                LinkedHashMap<Category, BigDecimal> limits = new LinkedHashMap<>();
                budget.getCategoryLimits().forEach((category, limit) ->
                        limits.put(mapCategory(category), limit));
                if (!dryRun) {
                    target.budgets().save(new MonthlyBudget(month,
                            budget.getOverallLimit(), limits));
                }
                count(imported, WorkspaceSections.MONTHLY_BUDGETS);
            });
        }
    }

    private void copyBudgetPlans() {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (BudgetPlan plan : target.budgetPlans().findAll()) {
            identifiers.add(plan.getIdentifier());
            fingerprints.add(planFingerprint(plan));
        }
        for (BudgetPlan plan : source.budgetPlans().findAll()) {
            attempt(WorkspaceSections.BUDGET_PLANS, plan.getIdentifier(), () -> {
                if (identifiers.contains(plan.getIdentifier())
                        || fingerprints.contains(planFingerprint(plan))) {
                    count(skipped, WorkspaceSections.BUDGET_PLANS);
                    return;
                }
                LinkedHashMap<Category, BigDecimal> limits = new LinkedHashMap<>();
                plan.getCategoryLimits().forEach((category, limit) ->
                        limits.put(mapCategory(category), limit));
                BudgetPlan mapped = new BudgetPlan(plan.getIdentifier(),
                        plan.getName(), plan.getStartDate(), plan.getEndDate(),
                        plan.getOverallLimit().orElse(null), limits,
                        plan.getRolloverMode(), plan.isActive());
                if (!dryRun) {
                    target.budgetPlans().add(mapped);
                }
                identifiers.add(mapped.getIdentifier());
                fingerprints.add(planFingerprint(mapped));
                count(imported, WorkspaceSections.BUDGET_PLANS);
            });
        }
    }

    private void copyRecurringEntries() {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (RecurringEntry entry : target.recurring().findAll()) {
            identifiers.add(entry.getIdentifier());
            fingerprints.add(recurringFingerprint(entry));
        }
        for (RecurringEntry entry : source.recurring().findAll()) {
            attempt(WorkspaceSections.RECURRING, entry.getIdentifier(), () -> {
                if (identifiers.contains(entry.getIdentifier())
                        || fingerprints.contains(recurringFingerprint(entry))) {
                    count(skipped, WorkspaceSections.RECURRING);
                    return;
                }
                RecurringEntry mapped = new RecurringEntry(
                        entry.getIdentifier(), entry.getType(),
                        entry.getAmount(), entry.getDescription(),
                        entry.getCategory().map(this::mapCategory).orElse(null),
                        mapAccount(entry.getSourceAccount()),
                        entry.getDestinationAccount()
                                .map(this::mapAccount).orElse(null),
                        entry.getFrequency(), entry.getInterval(),
                        entry.getStartDate(), entry.getEndDate().orElse(null),
                        entry.getNextDueDate(), entry.getKind(),
                        entry.getReminderDays(), entry.isActive());
                if (!dryRun) {
                    target.recurring().add(mapped);
                }
                identifiers.add(mapped.getIdentifier());
                fingerprints.add(recurringFingerprint(mapped));
                count(imported, WorkspaceSections.RECURRING);
            });
        }
    }

    private void copySavingsGoals() {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (SavingsGoal goal : target.goals().findAllGoals()) {
            identifiers.add(goal.getIdentifier());
            fingerprints.add(goalFingerprint(goal));
        }
        for (SavingsGoal goal : source.goals().findAllGoals()) {
            attempt(WorkspaceSections.GOALS, goal.getIdentifier(), () -> {
                String identifier = goal.getIdentifier();
                boolean duplicate = identifiers.contains(identifier)
                        || fingerprints.contains(goalFingerprint(goal));
                if (duplicate) {
                    count(skipped, WorkspaceSections.GOALS);
                } else {
                    SavingsGoal mapped = new SavingsGoal(identifier,
                            goal.getName(), goal.getTargetAmount(),
                            goal.getTargetDate(),
                            mapAccount(goal.getLinkedAccount()),
                            goal.isActive());
                    if (!dryRun) {
                        target.goals().addGoal(mapped);
                    }
                    identifiers.add(identifier);
                    fingerprints.add(goalFingerprint(mapped));
                    count(imported, WorkspaceSections.GOALS);
                }
                copyContributions(identifier, duplicate);
            });
        }
    }

    private void copyContributions(String goalIdentifier, boolean goalExisted) {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        if (goalExisted) {
            for (GoalContribution stored
                    : target.goals().findContributions(goalIdentifier)) {
                identifiers.add(stored.getIdentifier());
                fingerprints.add(contributionFingerprint(stored));
            }
        }
        for (GoalContribution contribution
                : source.goals().findContributions(goalIdentifier)) {
            attempt(WorkspaceSections.CONTRIBUTIONS,
                    contribution.getIdentifier(), () -> {
                        if (identifiers.contains(contribution.getIdentifier())
                                || fingerprints.contains(
                                        contributionFingerprint(contribution))) {
                            count(skipped, WorkspaceSections.CONTRIBUTIONS);
                            return;
                        }
                        if (!dryRun) {
                            target.goals().addContribution(contribution);
                        }
                        identifiers.add(contribution.getIdentifier());
                        fingerprints.add(contributionFingerprint(contribution));
                        count(imported, WorkspaceSections.CONTRIBUTIONS);
                    });
        }
    }

    private void copyDebts() {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (DebtRecord debt : target.debts().findAllDebts()) {
            identifiers.add(debt.getIdentifier());
            fingerprints.add(debtFingerprint(debt));
        }
        for (DebtRecord debt : source.debts().findAllDebts()) {
            attempt(WorkspaceSections.DEBTS, debt.getIdentifier(), () -> {
                boolean duplicate = identifiers.contains(debt.getIdentifier())
                        || fingerprints.contains(debtFingerprint(debt));
                if (duplicate) {
                    count(skipped, WorkspaceSections.DEBTS);
                } else {
                    if (!dryRun) {
                        target.debts().addDebt(debt);
                    }
                    identifiers.add(debt.getIdentifier());
                    fingerprints.add(debtFingerprint(debt));
                    count(imported, WorkspaceSections.DEBTS);
                }
                copyRepayments(debt.getIdentifier(), duplicate);
            });
        }
    }

    private void copyRepayments(String debtIdentifier, boolean debtExisted) {
        Set<String> identifiers = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        if (debtExisted) {
            for (DebtRepayment stored
                    : target.debts().findRepayments(debtIdentifier)) {
                identifiers.add(stored.getIdentifier());
                fingerprints.add(repaymentFingerprint(stored));
            }
        }
        for (DebtRepayment repayment
                : source.debts().findRepayments(debtIdentifier)) {
            attempt(WorkspaceSections.REPAYMENTS, repayment.getIdentifier(),
                    () -> {
                        if (identifiers.contains(repayment.getIdentifier())
                                || fingerprints.contains(
                                        repaymentFingerprint(repayment))) {
                            count(skipped, WorkspaceSections.REPAYMENTS);
                            return;
                        }
                        if (!dryRun) {
                            target.debts().addRepayment(repayment);
                        }
                        identifiers.add(repayment.getIdentifier());
                        fingerprints.add(repaymentFingerprint(repayment));
                        count(imported, WorkspaceSections.REPAYMENTS);
                    });
        }
    }

    // ------------------------------------------------------------ fingerprints

    private static String expenseFingerprint(Expense expense) {
        return join(expense.getDate(), expense.getAmount(),
                expense.getDescription(), expense.getCategory().getIdentifier(),
                expense.getAccount().getIdentifier());
    }

    private static String incomeFingerprint(Income income) {
        return join(income.getDate(), income.getAmount(), income.getSource(),
                income.getAccount().getIdentifier());
    }

    private static String transferFingerprint(Transfer transfer) {
        return join(transfer.getDate(), transfer.getAmount(),
                transfer.getSourceAccount().getIdentifier(),
                transfer.getDestinationAccount().getIdentifier());
    }

    private static String planFingerprint(BudgetPlan plan) {
        return join(plan.getName(), plan.getStartDate(), plan.getEndDate());
    }

    private static String recurringFingerprint(RecurringEntry entry) {
        return join(entry.getType(), entry.getDescription(), entry.getAmount(),
                entry.getStartDate(), entry.getFrequency(), entry.getInterval());
    }

    private static String goalFingerprint(SavingsGoal goal) {
        return join(goal.getName(), goal.getTargetAmount(), goal.getTargetDate());
    }

    private static String contributionFingerprint(GoalContribution item) {
        return join(item.getGoalIdentifier(), item.getDate(), item.getAmount(),
                item.getNote());
    }

    private static String debtFingerprint(DebtRecord debt) {
        return join(debt.getDirection(), debt.getCounterparty(),
                debt.getOriginalAmount(), debt.getDueDate());
    }

    private static String repaymentFingerprint(DebtRepayment repayment) {
        return join(repayment.getDebtIdentifier(), repayment.getDate(),
                repayment.getAmount(), repayment.getNote());
    }

    /**
     * Builds a comparison key. Amounts drop trailing zeros and text is compared
     * without case or padding so that the same record written by two different
     * versions still matches.
     */
    private static String join(Object... values) {
        StringBuilder key = new StringBuilder();
        for (Object value : values) {
            key.append(SEPARATOR);
            if (value instanceof BigDecimal amount) {
                key.append(amount.stripTrailingZeros().toPlainString());
            } else if (value instanceof LocalDate date) {
                key.append(date);
            } else if (value != null) {
                key.append(value.toString().strip().toLowerCase(Locale.ROOT));
            }
        }
        return key.toString();
    }

    // ----------------------------------------------------------------- support

    private void attempt(String section, String reference, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            failures.add(new ImportIssue(section, reference,
                    describe(failure)));
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? "The record could not be saved."
                : message;
    }

    private static void count(Map<String, Integer> counts, String section) {
        counts.merge(section, 1, Integer::sum);
    }
}
