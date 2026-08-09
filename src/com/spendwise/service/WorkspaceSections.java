package com.spendwise.service;

import java.util.List;

/** Stable, user-visible names for the parts of a finance workspace. */
public final class WorkspaceSections {

    public static final String ACCOUNTS = "Accounts";
    public static final String CATEGORIES = "Categories";
    public static final String EXPENSES = "Expenses";
    public static final String INCOME = "Income";
    public static final String TRANSFERS = "Transfers";
    public static final String MONTHLY_BUDGETS = "Monthly budgets";
    public static final String BUDGET_PLANS = "Budget plans";
    public static final String RECURRING = "Recurring entries";
    public static final String GOALS = "Savings goals";
    public static final String CONTRIBUTIONS = "Goal contributions";
    public static final String DEBTS = "Debts";
    public static final String REPAYMENTS = "Debt repayments";

    public static final List<String> ORDER = List.of(
            ACCOUNTS, CATEGORIES, EXPENSES, INCOME, TRANSFERS,
            MONTHLY_BUDGETS, BUDGET_PLANS, RECURRING, GOALS, CONTRIBUTIONS,
            DEBTS, REPAYMENTS);

    private WorkspaceSections() {
    }
}
