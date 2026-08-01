package com.spendwise.model;

public enum RecurringEntryType {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer");

    private final String displayName;

    RecurringEntryType(String displayName) {
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
