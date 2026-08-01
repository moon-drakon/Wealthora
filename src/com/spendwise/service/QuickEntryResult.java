package com.spendwise.service;

import com.spendwise.model.RecurringEntryType;
import java.util.Objects;

public record QuickEntryResult(RecurringEntryType type, String identifier) {

    public QuickEntryResult {
        Objects.requireNonNull(type, "Quick-entry type is required.");
        Objects.requireNonNull(identifier, "Quick-entry identifier is required.");
    }
}
