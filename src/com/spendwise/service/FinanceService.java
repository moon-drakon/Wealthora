package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
        for (Income income : incomeService.getAllIncome()) {
            apply(balances, income.getAccount(), income.getAmount());
        }
        for (Expense expense : expenseService.getAllExpenses()) {
            apply(
                    balances,
                    expense.getAccount(),
                    expense.getAmount().negate());
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
