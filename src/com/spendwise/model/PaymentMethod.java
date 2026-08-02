package com.spendwise.model;

public enum PaymentMethod {
    UNSPECIFIED("Unspecified"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer"),
    MOBILE_BANKING("Mobile Banking"),
    DIGITAL_WALLET("Digital Wallet"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    CHECK("Check"),
    OTHER("Other");

    private final String displayName;

    PaymentMethod(String displayName) {
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
