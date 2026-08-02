package com.spendwise.model;

public enum DebtDirection {
    BORROWED("Borrowed"),
    LENT("Lent");

    private final String displayName;
    DebtDirection(String displayName) { this.displayName = displayName; }
    @Override public String toString() { return displayName; }
}
