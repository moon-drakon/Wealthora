package com.spendwise.model;

public enum RecurringKind {
    SCHEDULED_TRANSACTION("Scheduled transaction"),
    BILL("Bill"),
    SUBSCRIPTION("Subscription");

    private final String displayName;

    RecurringKind(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
