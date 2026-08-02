package com.spendwise.service;

import com.spendwise.model.RecurringEntry;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record UpcomingRecurringItem(
        RecurringEntry definition, LocalDate dueDate, long daysUntilDue) {

    public UpcomingRecurringItem {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(dueDate);
    }

    public static UpcomingRecurringItem from(
            RecurringEntry entry, LocalDate referenceDate) {
        return new UpcomingRecurringItem(entry, entry.getNextDueDate(),
                ChronoUnit.DAYS.between(referenceDate, entry.getNextDueDate()));
    }
}
