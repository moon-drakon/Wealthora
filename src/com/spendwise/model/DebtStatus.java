package com.spendwise.model;

public enum DebtStatus {
    OPEN("Open"),
    PARTIALLY_REPAID("Partially repaid"),
    OVERDUE("Overdue"),
    PAID("Paid");

    private final String displayName;
    DebtStatus(String displayName) { this.displayName = displayName; }
    @Override public String toString() { return displayName; }
}
