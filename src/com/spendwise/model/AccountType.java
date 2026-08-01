package com.spendwise.model;

public enum AccountType {
    CASH("Cash"),
    BANK("Bank"),
    MOBILE_WALLET("Mobile Wallet"),
    CARD("Card"),
    OTHER("Other");

    private final String displayName;

    AccountType(String displayName) {
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
