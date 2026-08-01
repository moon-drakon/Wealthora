package com.spendwise.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CalendarMonthSnapshot {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final YearMonth month;
    private final int firstDayColumn;
    private final Map<LocalDate, DailyActivitySnapshot> days;

    public CalendarMonthSnapshot(
            YearMonth month,
            int firstDayColumn,
            Map<LocalDate, DailyActivitySnapshot> days) {
        this.month = Objects.requireNonNull(month, "Calendar month is required.");
        if (firstDayColumn < 0 || firstDayColumn > 6) {
            throw new IllegalArgumentException(
                    "Calendar first-day column must be from 0 through 6.");
        }
        this.firstDayColumn = firstDayColumn;
        Objects.requireNonNull(days, "Calendar days are required.");
        LinkedHashMap<LocalDate, DailyActivitySnapshot> copy =
                new LinkedHashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            DailyActivitySnapshot snapshot = days.get(date);
            if (snapshot == null) {
                snapshot = new DailyActivitySnapshot(
                        date, ZERO, ZERO, java.util.List.of());
            }
            copy.put(date, snapshot);
        }
        this.days = Collections.unmodifiableMap(copy);
    }

    public YearMonth getMonth() {
        return month;
    }

    public int getFirstDayColumn() {
        return firstDayColumn;
    }

    public Map<LocalDate, DailyActivitySnapshot> getDays() {
        return days;
    }

    public DailyActivitySnapshot getDay(LocalDate date) {
        DailyActivitySnapshot snapshot = days.get(Objects.requireNonNull(
                date, "Calendar date is required."));
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Date is outside the selected calendar month: " + date);
        }
        return snapshot;
    }

    public boolean hasActivity() {
        return days.values().stream().anyMatch(DailyActivitySnapshot::hasActivity);
    }
}
