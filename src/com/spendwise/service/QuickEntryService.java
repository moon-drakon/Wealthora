package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.validation.ValidationException;
import com.spendwise.voice.VoiceTransactionDraft;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class QuickEntryService {

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final RecurringService recurringService;

    public QuickEntryService(
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService) {
        this(expenseService, incomeService, transferService, null);
    }

    public QuickEntryService(
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            RecurringService recurringService) {
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.recurringService = recurringService;
    }

    public QuickEntryResult createEntry(
            RecurringEntryType type,
            LocalDate date,
            BigDecimal amount,
            String description,
            Category category,
            Account sourceAccount,
            Account destinationAccount) {
        RecurringEntryType requiredType = Objects.requireNonNull(
                type, "Quick-entry type is required.");
        return switch (requiredType) {
            case EXPENSE -> new QuickEntryResult(
                    requiredType,
                    expenseService.createExpense(
                            description,
                            amount,
                            date,
                            category,
                            sourceAccount,
                            "Quick entry")
                            .getId());
            case INCOME -> new QuickEntryResult(
                    requiredType,
                    incomeService.createIncome(
                            date,
                            amount,
                            description,
                            sourceAccount,
                            "Quick entry")
                            .getId());
            case TRANSFER -> new QuickEntryResult(
                    requiredType,
                    transferService.createTransfer(
                            date,
                            amount,
                            sourceAccount,
                            destinationAccount,
                            description)
                            .getId());
        };
    }

    public QuickEntryResult confirmVoiceDraft(VoiceTransactionDraft draft) {
        VoiceTransactionDraft required = Objects.requireNonNull(
                draft, "Voice transaction draft is required.");
        List<String> problems = required.findValidationProblems();
        if (!problems.isEmpty()) {
            throw new ValidationException(String.join(" ", problems));
        }
        RecurringEntryType type = RecurringEntryType.valueOf(
                required.getTransactionType().name());
        if (required.isRecurring()) {
            if (recurringService == null) {
                throw new ValidationException(
                        "Recurring Voice Quick Entry is unavailable.");
            }
            return new QuickEntryResult(type, recurringService.addDefinition(
                    type,
                    required.getAmount(),
                    required.getDescription(),
                    type == RecurringEntryType.EXPENSE
                            ? required.getEffectiveCategory() : null,
                    required.getSourceAccount(),
                    type == RecurringEntryType.TRANSFER
                            ? required.getDestinationAccount() : null,
                    required.getRecurringFrequency(),
                    1,
                    required.getNextDueDate(),
                    null,
                    true).getIdentifier());
        }
        return switch (type) {
            case EXPENSE -> new QuickEntryResult(type,
                    expenseService.createExpense(
                            required.getDescription(),
                            required.getAmount(),
                            required.getDate(),
                            required.getEffectiveCategory(),
                            required.getSourceAccount(),
                            required.getPaymentMethod(),
                            required.getTags(),
                            required.getNote()).getId());
            case INCOME -> new QuickEntryResult(type,
                    incomeService.createIncome(
                            required.getDate(),
                            required.getAmount(),
                            required.getDescription(),
                            required.getSourceAccount(),
                            required.getPaymentMethod(),
                            required.getTags(),
                            required.getNote()).getId());
            case TRANSFER -> new QuickEntryResult(type,
                    transferService.createTransfer(
                            required.getDate(),
                            required.getAmount(),
                            required.getSourceAccount(),
                            required.getDestinationAccount(),
                            required.getNote().isBlank()
                                    ? required.getDescription()
                                    : required.getNote()).getId());
        };
    }
}
