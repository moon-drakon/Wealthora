package com.spendwise.service;

public enum FinancialEntryType {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer");

    private final String displayName;

    FinancialEntryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
