package com.spendwise.service;

import java.util.List;

public final class ManagedDataFiles {

    public static final List<String> FILE_NAMES = List.of(
            "expenses.csv",
            "budgets.csv",
            "categories.csv",
            "accounts.csv",
            "account-settings.csv",
            "income.csv",
            "transfers.csv",
            "recurring.csv",
            "cards.csv",
            "currency-settings.csv");

    private ManagedDataFiles() {
    }
}
