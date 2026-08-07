package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transaction;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FinanceService {

    private final AccountService accountService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;

    public FinanceService(
            AccountService accountService,
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService) {
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
    }

    public AccountBalanceSnapshot calculateBalances() {
        LinkedHashMap<Account, BigDecimal> balances =
                new LinkedHashMap<>();
        for (Account account : accountService.listAllAccounts()) {
            balances.put(account, account.getOpeningBalance());
        }
        for (Transaction transaction : getAllTransactions()) {
            apply(balances, transaction.getAccount(),
                    transaction.calculateImpact());
        }
        for (Transfer transfer : transferService.getAllTransfers()) {
            apply(
                    balances,
                    transfer.getSourceAccount(),
                    transfer.getAmount().negate());
            apply(
                    balances,
                    transfer.getDestinationAccount(),
                    transfer.getAmount());
        }
        return new AccountBalanceSnapshot(balances);
    }

    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.addAll(incomeService.getAllIncome());
        transactions.addAll(expenseService.getAllExpenses());
        return List.copyOf(transactions);
    }

    public BigDecimal calculateImpact(
            Iterable<? extends Transaction> transactions) {
        Objects.requireNonNull(transactions, "Transactions are required.");
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            total = total.add(Objects.requireNonNull(transaction,
                    "Transactions cannot contain null elements.")
                    .calculateImpact());
        }
        return total;
    }

    private static void apply(
            Map<Account, BigDecimal> balances,
            Account account,
            BigDecimal change) {
        BigDecimal current = balances.get(account);
        if (current == null) {
            throw new IllegalStateException(
                    "Transaction references an unknown account: "
                    + account.getIdentifier());
        }
        balances.put(account, current.add(change));
    }
}
