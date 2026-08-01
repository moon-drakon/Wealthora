package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public enum RecurrenceFrequency {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    private final String displayName;

    RecurrenceFrequency(String displayName) {
        this.displayName = displayName;
    }

    public LocalDate nextDate(
            LocalDate currentDate, int interval, LocalDate anchorDate) {
        LocalDate current = Objects.requireNonNull(
                currentDate, "Current due date is required.");
        LocalDate anchor = Objects.requireNonNull(
                anchorDate, "Recurrence anchor date is required.");
        if (interval <= 0) {
            throw new ValidationException(
                    "Recurrence interval must be greater than zero.");
        }
        return switch (this) {
            case DAILY -> current.plusDays(interval);
            case WEEKLY -> current.plusWeeks(interval);
            case MONTHLY -> anchoredMonthDate(
                    YearMonth.from(current).plusMonths(interval),
                    anchor.getDayOfMonth());
            case YEARLY -> anchoredMonthDate(
                    YearMonth.of(
                            Math.addExact(current.getYear(), interval),
                            anchor.getMonth()),
                    anchor.getDayOfMonth());
        };
    }

    private static LocalDate anchoredMonthDate(
            YearMonth month, int preferredDay) {
        return month.atDay(Math.min(preferredDay, month.lengthOfMonth()));
    }

    @Override
    public String toString() {
        return displayName;
    }
}
