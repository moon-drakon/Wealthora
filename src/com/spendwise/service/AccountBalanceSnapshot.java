package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class AccountBalanceSnapshot {

    private final Map<Account, BigDecimal> balances;
    private final BigDecimal totalBalance;

    public AccountBalanceSnapshot(Map<Account, BigDecimal> balances) {
        Objects.requireNonNull(balances, "Account balances are required.");
        LinkedHashMap<Account, BigDecimal> normalized = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Account, BigDecimal> entry : balances.entrySet()) {
            Account account = Objects.requireNonNull(
                    entry.getKey(), "Account balances cannot contain null accounts.");
            BigDecimal balance = Objects.requireNonNull(
                    entry.getValue(), "An account balance cannot be null.")
                    .setScale(2, RoundingMode.UNNECESSARY);
            normalized.put(account, balance);
            total = total.add(balance);
        }
        this.balances = Collections.unmodifiableMap(
                new LinkedHashMap<>(normalized));
        this.totalBalance = total.setScale(2, RoundingMode.UNNECESSARY);
    }

    public Map<Account, BigDecimal> getBalances() {
        return balances;
    }

    public BigDecimal getBalance(Account account) {
        Account required = Objects.requireNonNull(
                account, "Account is required.");
        BigDecimal balance = balances.get(required);
        if (balance == null) {
            throw new ValidationException(
                    "The requested account is not part of this balance snapshot.");
        }
        return balance;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }
}
