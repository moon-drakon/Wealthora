package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurringEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class QuickEntryService {

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;

    public QuickEntryService(
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService) {
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
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
}
