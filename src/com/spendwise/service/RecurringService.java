package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import com.spendwise.repository.RecurringEntryRepository;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class RecurringService {

    private static final DateTimeFormatter OCCURRENCE_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final RecurringEntryRepository repository;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public RecurringService(
            RecurringEntryRepository repository,
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            AccountService accountService,
            CategoryService categoryService) {
        this.repository = Objects.requireNonNull(
                repository, "Recurring repository is required.");
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.categoryService = Objects.requireNonNull(
                categoryService, "Category service is required.");
    }

    public List<RecurringEntry> listAll() {
        return List.copyOf(repository.findAll());
    }

    public RecurringEntry addDefinition(
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
        return addDefinition(type, amount, description, category, sourceAccount,
                destinationAccount, frequency, interval, startDate, endDate,
                RecurringKind.SCHEDULED_TRANSACTION, 3, active);
    }

    public RecurringEntry addDefinition(
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
        ValidatedReferences references = validateNewReferences(
                type, category, sourceAccount, destinationAccount);
        RecurringEntry entry = RecurringEntry.create(
                type,
                amount,
                description,
                references.category(),
                references.source(),
                references.destination(),
                frequency,
                interval,
                startDate,
                endDate,
                kind,
                reminderDays,
                active);
        repository.add(entry);
        return entry;
    }

    public RecurringEntry updateDefinition(
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
        RecurringEntry existing = requireDefinition(identifier);
        return updateDefinition(identifier, type, amount, description, category,
                sourceAccount, destinationAccount, frequency, interval,
                startDate, endDate, nextDueDate, existing.getKind(),
                existing.getReminderDays(), active);
    }

    public RecurringEntry updateDefinition(
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
        RecurringEntry existing = requireDefinition(identifier);
        ValidatedReferences references = validateUpdatedReferences(
                existing,
                type,
                category,
                sourceAccount,
                destinationAccount);
        RecurringEntry replacement = new RecurringEntry(
                existing.getIdentifier(),
                type,
                amount,
                description,
                references.category(),
                references.source(),
                references.destination(),
                frequency,
                interval,
                startDate,
                endDate,
                nextDueDate,
                kind,
                reminderDays,
                active);
        if (active) {
            validateForPosting(replacement);
        }
        repository.update(replacement);
        return replacement;
    }

    public RecurringEntry setActive(String identifier, boolean active) {
        RecurringEntry existing = requireDefinition(identifier);
        if (active) {
            validateForPosting(existing);
        }
        RecurringEntry replacement = existing.withActive(active);
        repository.update(replacement);
        return replacement;
    }

    public List<RecurringEntry> findDueEntries(LocalDate throughDate) {
        LocalDate date = validateThroughDate(throughDate);
        return repository.findAll().stream()
                .filter(entry -> entry.isDueOnOrBefore(date))
                .toList();
    }

    public List<UpcomingRecurringItem> findUpcoming(
            LocalDate referenceDate, int daysAhead) {
        LocalDate reference = Objects.requireNonNull(
                referenceDate, "Reminder reference date is required.");
        if (daysAhead < 0 || daysAhead > 3650) {
            throw new ValidationException(
                    "Reminder range must be from 0 through 3650 days.");
        }
        LocalDate through = reference.plusDays(daysAhead);
        return repository.findAll().stream()
                .filter(RecurringEntry::isActive)
                .filter(entry -> !entry.getNextDueDate().isBefore(reference))
                .filter(entry -> !entry.getNextDueDate().isAfter(through))
                .filter(entry -> !entry.getNextDueDate().isAfter(
                        reference.plusDays(entry.getReminderDays())))
                .sorted(java.util.Comparator
                        .comparing(RecurringEntry::getNextDueDate)
                        .thenComparing(RecurringEntry::getIdentifier))
                .map(entry -> UpcomingRecurringItem.from(entry, reference))
                .toList();
    }

    public RecurringGenerationResult generateDueEntries(
            LocalDate throughDate) {
        LocalDate date = validateThroughDate(throughDate);
        List<RecurringEntry> dueDefinitions = findDueEntries(date);
        for (RecurringEntry definition : dueDefinitions) {
            validateForPosting(definition);
        }

        int generated = 0;
        int recovered = 0;
        for (RecurringEntry initial : dueDefinitions) {
            RecurringEntry current = initial;
            while (current.isDueOnOrBefore(date)) {
                LocalDate occurrenceDate = current.getNextDueDate();
                if (postOccurrenceIfMissing(current, occurrenceDate)) {
                    generated++;
                } else {
                    recovered++;
                }
                LocalDate following = current.calculateFollowingDueDate();
                boolean remainsActive = current.getEndDate()
                        .map(end -> !following.isAfter(end))
                        .orElse(true);
                current = current.withNextDueDate(
                        following, remainsActive);
                repository.update(current);
            }
        }
        return new RecurringGenerationResult(generated, recovered);
    }

    private boolean postOccurrenceIfMissing(
            RecurringEntry definition, LocalDate occurrenceDate) {
        String occurrenceSuffix = definition.getIdentifier()
                .substring("RECURRING_".length())
                + "_" + occurrenceDate.format(OCCURRENCE_DATE);
        return switch (definition.getType()) {
            case EXPENSE -> {
                String id = "RECURRING_" + occurrenceSuffix;
                if (expenseService.findExpenseById(id).isPresent()) {
                    yield false;
                }
                expenseService.createExpenseWithId(
                        id,
                        definition.getDescription(),
                        definition.getAmount(),
                        occurrenceDate,
                        definition.getCategory().orElseThrow(),
                        definition.getSourceAccount(),
                        "Generated from recurring entry");
                yield true;
            }
            case INCOME -> {
                String id = "INCOME_RECUR_" + occurrenceSuffix;
                if (incomeService.findById(id).isPresent()) {
                    yield false;
                }
                incomeService.createIncomeWithId(
                        id,
                        occurrenceDate,
                        definition.getAmount(),
                        definition.getDescription(),
                        definition.getSourceAccount(),
                        "Generated from recurring entry");
                yield true;
            }
            case TRANSFER -> {
                String id = "TRANSFER_RECUR_" + occurrenceSuffix;
                if (transferService.findById(id).isPresent()) {
                    yield false;
                }
                transferService.createTransferWithId(
                        id,
                        occurrenceDate,
                        definition.getAmount(),
                        definition.getSourceAccount(),
                        definition.getDestinationAccount().orElseThrow(),
                        definition.getDescription());
                yield true;
            }
        };
    }

    private RecurringEntry requireDefinition(String identifier) {
        String normalized = FinanceValidator.validateIdentifier(
                identifier, "Recurring entry", "RECURRING_");
        return repository.findById(normalized)
                .orElseThrow(() -> new ValidationException(
                        "Recurring entry was not found."));
    }

    private ValidatedReferences validateNewReferences(
            RecurringEntryType type,
            Category category,
            Account source,
            Account destination) {
        RecurringEntryType requiredType = Objects.requireNonNull(
                type, "Recurring entry type is required.");
        Account validatedSource = accountService.requireSelectable(source);
        Category validatedCategory = requiredType == RecurringEntryType.EXPENSE
                ? requireSelectableCategory(category)
                : null;
        Account validatedDestination =
                requiredType == RecurringEntryType.TRANSFER
                ? accountService.requireSelectable(destination)
                : null;
        return new ValidatedReferences(
                validatedCategory, validatedSource, validatedDestination);
    }

    private ValidatedReferences validateUpdatedReferences(
            RecurringEntry existing,
            RecurringEntryType type,
            Category category,
            Account source,
            Account destination) {
        RecurringEntryType requiredType = Objects.requireNonNull(
                type, "Recurring entry type is required.");
        Account validatedSource = accountService.requireSelectableOrHistorical(
                source, existing.getSourceAccount());
        Category validatedCategory = null;
        if (requiredType == RecurringEntryType.EXPENSE) {
            Category historical = existing.getCategory().orElse(null);
            validatedCategory = requireSelectableOrHistoricalCategory(
                    category, historical);
        }
        Account validatedDestination = null;
        if (requiredType == RecurringEntryType.TRANSFER) {
            validatedDestination = accountService.requireSelectableOrHistorical(
                    destination,
                    existing.getDestinationAccount().orElse(null));
        }
        return new ValidatedReferences(
                validatedCategory, validatedSource, validatedDestination);
    }

    private void validateForPosting(RecurringEntry entry) {
        accountService.requireSelectable(entry.getSourceAccount());
        if (entry.getType() == RecurringEntryType.EXPENSE) {
            requireSelectableCategory(entry.getCategory().orElseThrow());
        } else if (entry.getType() == RecurringEntryType.TRANSFER) {
            accountService.requireSelectable(
                    entry.getDestinationAccount().orElseThrow());
        }
    }

    private Category requireSelectableCategory(Category category) {
        Category current = categoryService.resolveCategory(
                Objects.requireNonNull(
                        category, "Recurring expense category is required.")
                        .getIdentifier());
        if (!current.isActive()) {
            throw new ValidationException(
                    "Archived categories cannot be used for recurring expenses.");
        }
        return current;
    }

    private Category requireSelectableOrHistoricalCategory(
            Category category, Category historical) {
        Category required = Objects.requireNonNull(
                category, "Recurring expense category is required.");
        if (historical != null && required.equals(historical)) {
            return categoryService.resolveCategory(required.getIdentifier());
        }
        return requireSelectableCategory(required);
    }

    private static LocalDate validateThroughDate(LocalDate date) {
        return FinanceValidator.validatePostedDate(
                date, "Recurring generation date");
    }

    private record ValidatedReferences(
            Category category, Account source, Account destination) {
    }
}
