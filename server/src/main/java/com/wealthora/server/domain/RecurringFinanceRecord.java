package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_entries")
public class RecurringFinanceRecord {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "entry_type", nullable = false, length = 40)
    private String entryType;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 160) private String description;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;
    @Column(name = "destination_account_id") private UUID destinationAccountId;
    @Column(nullable = false, length = 40) private String frequency;
    @Column(name = "recurrence_interval", nullable = false)
    private int recurrenceInterval;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "next_due_date", nullable = false) private LocalDate nextDueDate;
    @Column(name = "recurring_kind", nullable = false, length = 40)
    private String recurringKind;
    @Column(name = "reminder_days", nullable = false) private int reminderDays;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected RecurringFinanceRecord() {
    }

    public RecurringFinanceRecord(
            UUID id, UUID userId, String externalId, String entryType,
            BigDecimal amount, String description, UUID categoryId,
            UUID sourceAccountId, UUID destinationAccountId, String frequency,
            int recurrenceInterval, LocalDate startDate, LocalDate endDate,
            LocalDate nextDueDate, String recurringKind, int reminderDays,
            boolean active, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        this.categoryId = categoryId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.frequency = frequency;
        this.recurrenceInterval = recurrenceInterval;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextDueDate = nextDueDate;
        this.recurringKind = recurringKind;
        this.reminderDays = reminderDays;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public String getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public String getFrequency() { return frequency; }
    public int getRecurrenceInterval() { return recurrenceInterval; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public String getRecurringKind() { return recurringKind; }
    public int getReminderDays() { return reminderDays; }
    public boolean isActive() { return active; }

    public void update(
            String entryType, BigDecimal amount, String description,
            UUID categoryId, UUID sourceAccountId, UUID destinationAccountId,
            String frequency, int recurrenceInterval, LocalDate startDate,
            LocalDate endDate, LocalDate nextDueDate, String recurringKind,
            int reminderDays, boolean active, Instant now) {
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        this.categoryId = categoryId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.frequency = frequency;
        this.recurrenceInterval = recurrenceInterval;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextDueDate = nextDueDate;
        this.recurringKind = recurringKind;
        this.reminderDays = reminderDays;
        this.active = active;
        this.updatedAt = now;
    }
}
