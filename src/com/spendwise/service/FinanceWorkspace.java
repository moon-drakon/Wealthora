package com.spendwise.service;

import com.spendwise.repository.AccountPreferenceRepository;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.BudgetPlanRepository;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.CsvAccountPreferenceRepository;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvBudgetPlanRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvDebtRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvSavingsGoalRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.repository.DebtRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.repository.RecurringEntryRepository;
import com.spendwise.repository.SavingsGoalRepository;
import com.spendwise.repository.TransferRepository;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One finance workspace expressed as its repositories.
 *
 * <p>The workspace supplies CSV repositories over one project-local user
 * folder. Backup, restore, import, and export share this repository bundle.
 */
public final class FinanceWorkspace {

    private final AccountRepository accounts;
    private final AccountPreferenceRepository accountPreference;
    private final CategoryRepository categories;
    private final ExpenseRepository expenses;
    private final IncomeRepository income;
    private final TransferRepository transfers;
    private final BudgetRepository budgets;
    private final BudgetPlanRepository budgetPlans;
    private final RecurringEntryRepository recurring;
    private final SavingsGoalRepository goals;
    private final DebtRepository debts;

    public FinanceWorkspace(
            AccountRepository accounts,
            AccountPreferenceRepository accountPreference,
            CategoryRepository categories,
            ExpenseRepository expenses,
            IncomeRepository income,
            TransferRepository transfers,
            BudgetRepository budgets,
            BudgetPlanRepository budgetPlans,
            RecurringEntryRepository recurring,
            SavingsGoalRepository goals,
            DebtRepository debts) {
        this.accounts = Objects.requireNonNull(
                accounts, "Account repository is required.");
        this.accountPreference = Objects.requireNonNull(
                accountPreference, "Account preference repository is required.");
        this.categories = Objects.requireNonNull(
                categories, "Category repository is required.");
        this.expenses = Objects.requireNonNull(
                expenses, "Expense repository is required.");
        this.income = Objects.requireNonNull(
                income, "Income repository is required.");
        this.transfers = Objects.requireNonNull(
                transfers, "Transfer repository is required.");
        this.budgets = Objects.requireNonNull(
                budgets, "Budget repository is required.");
        this.budgetPlans = Objects.requireNonNull(
                budgetPlans, "Budget plan repository is required.");
        this.recurring = Objects.requireNonNull(
                recurring, "Recurring entry repository is required.");
        this.goals = Objects.requireNonNull(
                goals, "Savings goal repository is required.");
        this.debts = Objects.requireNonNull(
                debts, "Debt repository is required.");
    }

    /**
     * Opens the managed CSV files in {@code directory} as a workspace. The
     * folder may be empty; every repository creates its file on first write.
     */
    public static FinanceWorkspace overDirectory(Path directory) {
        Path base = Objects.requireNonNull(
                directory, "Workspace directory is required.")
                .toAbsolutePath().normalize();
        CsvCategoryRepository categories = new CsvCategoryRepository(
                base.resolve("categories.csv"));
        CategoryService categoryService = new CategoryService(categories);
        CsvAccountRepository accounts = new CsvAccountRepository(
                base.resolve("accounts.csv"));
        CsvAccountPreferenceRepository accountPreference =
                new CsvAccountPreferenceRepository(
                        base.resolve("account-settings.csv"));
        AccountService accountService = new AccountService(
                accounts, accountPreference);
        return new FinanceWorkspace(
                accounts,
                accountPreference,
                categories,
                new CsvExpenseRepository(base.resolve("expenses.csv"),
                        categoryService::resolveCategory,
                        accountService::resolveAccount),
                new CsvIncomeRepository(base.resolve("income.csv"),
                        accountService::resolveAccount),
                new CsvTransferRepository(base.resolve("transfers.csv"),
                        accountService::resolveAccount),
                new CsvBudgetRepository(base.resolve("budgets.csv"),
                        categoryService::resolveCategory),
                new CsvBudgetPlanRepository(base.resolve("budget-plans.csv"),
                        categoryService::resolveCategory),
                new CsvRecurringEntryRepository(base.resolve("recurring.csv"),
                        categoryService::resolveCategory,
                        accountService::resolveAccount),
                new CsvSavingsGoalRepository(base.resolve("savings-goals.csv"),
                        accountService::resolveAccount),
                new CsvDebtRepository(base.resolve("debts.csv")));
    }

    public AccountRepository accounts() {
        return accounts;
    }

    public AccountPreferenceRepository accountPreference() {
        return accountPreference;
    }

    public CategoryRepository categories() {
        return categories;
    }

    public ExpenseRepository expenses() {
        return expenses;
    }

    public IncomeRepository income() {
        return income;
    }

    public TransferRepository transfers() {
        return transfers;
    }

    public BudgetRepository budgets() {
        return budgets;
    }

    public BudgetPlanRepository budgetPlans() {
        return budgetPlans;
    }

    public RecurringEntryRepository recurring() {
        return recurring;
    }

    public SavingsGoalRepository goals() {
        return goals;
    }

    public DebtRepository debts() {
        return debts;
    }
}
