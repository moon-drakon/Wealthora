package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AccountStatementService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final AccountService accountService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final FinanceService financeService;

    public AccountStatementService(
            AccountService accountService,
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            FinanceService financeService) {
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.financeService = Objects.requireNonNull(
                financeService, "Finance service is required.");
    }

    public AccountStatementSnapshot getStatement(String accountIdentifier) {
        Account account = accountService.resolveAccount(accountIdentifier);
        BigDecimal incomeTotal = ZERO;
        BigDecimal expenseTotal = ZERO;
        BigDecimal incomingTotal = ZERO;
        BigDecimal outgoingTotal = ZERO;
        List<FinancialActivityEntry> entries = new ArrayList<>();

        for (Expense expense : expenseService.getAllExpenses()) {
            if (expense.getAccount().equals(account)) {
                expenseTotal = expenseTotal.add(expense.getAmount());
                entries.add(new FinancialActivityEntry(
                        FinancialEntryType.EXPENSE,
                        expense.getDate(),
                        expense.getAmount(),
                        account,
                        null,
                        expense.getCategory(),
                        expense.getDescription()));
            }
        }
        for (Income income : incomeService.getAllIncome()) {
            if (income.getAccount().equals(account)) {
                incomeTotal = incomeTotal.add(income.getAmount());
                entries.add(new FinancialActivityEntry(
                        FinancialEntryType.INCOME,
                        income.getDate(),
                        income.getAmount(),
                        account,
                        null,
                        null,
                        income.getSource()));
            }
        }
        for (Transfer transfer : transferService.getAllTransfers()) {
            boolean outgoing = transfer.getSourceAccount().equals(account);
            boolean incoming = transfer.getDestinationAccount().equals(account);
            if (outgoing) {
                outgoingTotal = outgoingTotal.add(transfer.getAmount());
            }
            if (incoming) {
                incomingTotal = incomingTotal.add(transfer.getAmount());
            }
            if (outgoing || incoming) {
                entries.add(new FinancialActivityEntry(
                        FinancialEntryType.TRANSFER,
                        transfer.getDate(),
                        transfer.getAmount(),
                        transfer.getSourceAccount(),
                        transfer.getDestinationAccount(),
                        null,
                        transfer.getNote().isBlank()
                                ? "Transfer" : transfer.getNote()));
            }
        }
        entries.sort(Comparator
                .comparing(FinancialActivityEntry::getDate).reversed()
                .thenComparing(FinancialActivityEntry::getType)
                .thenComparing(FinancialActivityEntry::getDescription,
                        String.CASE_INSENSITIVE_ORDER));
        BigDecimal balance = financeService.calculateBalances()
                .getBalance(account);
        return new AccountStatementSnapshot(
                account,
                incomeTotal,
                expenseTotal,
                incomingTotal,
                outgoingTotal,
                balance,
                entries);
    }
}
