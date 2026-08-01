package com.spendwise.service;

public record RecurringGenerationResult(
        int generatedCount, int recoveredOccurrenceCount) {

    public RecurringGenerationResult {
        if (generatedCount < 0 || recoveredOccurrenceCount < 0) {
            throw new IllegalArgumentException(
                    "Recurring generation counts cannot be negative.");
        }
    }

    public int processedCount() {
        return generatedCount + recoveredOccurrenceCount;
    }
}
