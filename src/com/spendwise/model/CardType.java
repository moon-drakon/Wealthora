package com.spendwise.model;

public enum CardType {
    CREDIT("Credit Card"),
    DEBIT("Debit Card");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
