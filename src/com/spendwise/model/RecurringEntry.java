package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RecurringEntry {

    private static final String ID_PREFIX = "RECURRING_";

    private final String identifier;
    private final RecurringEntryType type;
    private final BigDecimal amount;
    private final String description;
    private final Category category;
    private final Account sourceAccount;
    private final Account destinationAccount;
    private final RecurrenceFrequency frequency;
    private final int interval;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalDate nextDueDate;
    private final RecurringKind kind;
    private final int reminderDays;
    private final boolean active;

    public RecurringEntry(
            String identifier,
            RecurringEntryType type,
            BigDecimal amount,
            String description,
            Category category,
            Account sourceAccount,
            Account destinationAccount,
            RecurrenceFrequency frequency,
            int interval,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextDueDate,
            boolean active) {
        this(identifier, type, amount, description, category, sourceAccount,
                destinationAccount, frequency, interval, startDate, endDate,
                nextDueDate, RecurringKind.SCHEDULED_TRANSACTION, 3, active);
    }

    public RecurringEntry(
            String identifier,
            RecurringEntryType type,
            BigDecimal amount,
            String description,
            Category category,
            Account sourceAccount,
            Account destinationAccount,
            RecurrenceFrequency frequency,
            int interval,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextDueDate,
            RecurringKind kind,
            int reminderDays,
            boolean active) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Recurring entry", ID_PREFIX);
        this.type = Objects.requireNonNull(
                type, "Recurring entry type is required.");
        this.amount = FinanceValidator.validatePositiveAmount(
                amount, "Recurring amount");
        this.description = FinanceValidator.validateRequiredText(
                description,
                "Recurring description",
                FinanceValidator.MAX_NAME_LENGTH);
        this.sourceAccount = Objects.requireNonNull(
                sourceAccount, "Recurring source account is required.");
        this.frequency = Objects.requireNonNull(
                frequency, "Recurrence frequency is required.");
        if (interval <= 0) {
            throw new ValidationException(
                    "Recurrence interval must be greater than zero.");
        }
        this.interval = interval;
        this.startDate = Objects.requireNonNull(
                startDate, "Recurring start date is required.");
        this.endDate = endDate;
        this.nextDueDate = Objects.requireNonNull(
                nextDueDate, "Next due date is required.");
        this.kind = Objects.requireNonNull(
                kind, "Recurring kind is required.");
        if (reminderDays < 0 || reminderDays > 365) {
            throw new ValidationException(
                    "Reminder days must be from 0 through 365.");
        }
        this.reminderDays = reminderDays;
        this.active = active;
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new ValidationException(
                    "Recurring end date must not be before start date.");
        }
        if (nextDueDate.isBefore(startDate)) {
            throw new ValidationException(
                    "Next due date must not be before start date.");
        }
        if (active && endDate != null && nextDueDate.isAfter(endDate)) {
            throw new ValidationException(
                    "An active recurring entry cannot be due after its end date.");
        }

        if (type == RecurringEntryType.EXPENSE) {
            this.category = Objects.requireNonNull(
                    category, "Recurring expense category is required.");
            if (destinationAccount != null) {
                throw new ValidationException(
                        "A recurring expense cannot have a destination account.");
            }
            this.destinationAccount = null;
        } else if (type == RecurringEntryType.TRANSFER) {
            requireScheduledTransactionKind(kind);
            if (category != null) {
                throw new ValidationException(
                        "A recurring transfer cannot have an expense category.");
            }
            this.category = null;
            this.destinationAccount = Objects.requireNonNull(
                    destinationAccount,
                    "Recurring transfer destination account is required.");
            if (sourceAccount.equals(destinationAccount)) {
                throw new ValidationException(
                        "Recurring transfer accounts must be different.");
            }
        } else {
            requireScheduledTransactionKind(kind);
            if (category != null || destinationAccount != null) {
                throw new ValidationException(
                        "Recurring income cannot have a category or destination account.");
            }
            this.category = null;
            this.destinationAccount = null;
        }
    }

    private static void requireScheduledTransactionKind(RecurringKind kind) {
        if (kind != RecurringKind.SCHEDULED_TRANSACTION) {
            throw new ValidationException(
                    "Bills and subscriptions must be recurring expenses.");
        }
    }

    public static RecurringEntry create(
            RecurringEntryType type,
            BigDecimal amount,
            String description,
            Category category,
            Account sourceAccount,
            Account destinationAccount,
            RecurrenceFrequency frequency,
            int interval,
            LocalDate startDate,
            LocalDate endDate,
            boolean active) {
        return create(type, amount, description, category, sourceAccount,
                destinationAccount, frequency, interval, startDate, endDate,
                RecurringKind.SCHEDULED_TRANSACTION, 3, active);
    }

    public static RecurringEntry create(
            RecurringEntryType type,
            BigDecimal amount,
            String description,
            Category category,
            Account sourceAccount,
            Account destinationAccount,
            RecurrenceFrequency frequency,
            int interval,
            LocalDate startDate,
            LocalDate endDate,
            RecurringKind kind,
            int reminderDays,
            boolean active) {
        String identifier = ID_PREFIX + UUID.randomUUID().toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
        return new RecurringEntry(
                identifier,
                type,
                amount,
                description,
                category,
                sourceAccount,
                destinationAccount,
                frequency,
                interval,
                startDate,
                endDate,
                startDate,
                kind,
                reminderDays,
                active);
    }

    public RecurringEntry withNextDueDate(
            LocalDate newNextDueDate, boolean newActive) {
        return new RecurringEntry(
                identifier, type, amount, description, category,
                sourceAccount, destinationAccount, frequency, interval,
                startDate, endDate, newNextDueDate, kind, reminderDays,
                newActive);
    }

    public RecurringEntry withActive(boolean newActive) {
        return withNextDueDate(nextDueDate, newActive);
    }

    public String getIdentifier() {
        return identifier;
    }

    public RecurringEntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Optional<Category> getCategory() {
        return Optional.ofNullable(category);
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Optional<Account> getDestinationAccount() {
        return Optional.ofNullable(destinationAccount);
    }

    public RecurrenceFrequency getFrequency() {
        return frequency;
    }

    public int getInterval() {
        return interval;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public Optional<LocalDate> getEndDate() {
        return Optional.ofNullable(endDate);
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public RecurringKind getKind() {
        return kind;
    }

    public int getReminderDays() {
        return reminderDays;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDueOnOrBefore(LocalDate date) {
        LocalDate requiredDate = Objects.requireNonNull(
                date, "Due-through date is required.");
        return active
                && !nextDueDate.isAfter(requiredDate)
                && (endDate == null || !nextDueDate.isAfter(endDate));
    }

    public LocalDate calculateFollowingDueDate() {
        return frequency.nextDate(nextDueDate, interval, startDate);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RecurringEntry entry
                && identifier.equals(entry.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}
