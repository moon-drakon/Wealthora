package com.spendwise.model;

public enum BudgetRolloverMode {
    NONE("No rollover"),
    CARRY_UNUSED("Carry unused amount");

    private final String displayName;

    BudgetRolloverMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
