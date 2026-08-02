package com.spendwise.service;

import java.time.LocalDate;
import java.util.Objects;

public record FinanceNotification(
        String type,
        NotificationSeverity severity,
        String title,
        String message,
        LocalDate dueDate) {

    public FinanceNotification {
        if (type == null || type.isBlank()) throw new IllegalArgumentException();
        Objects.requireNonNull(severity);
        if (title == null || title.isBlank()) throw new IllegalArgumentException();
        if (message == null || message.isBlank()) throw new IllegalArgumentException();
    }
}
