package com.spendwise.service;

import com.spendwise.model.Account;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PortfolioAnalyticsSnapshot(
        AdvancedReportSnapshot transactionReport,
        Map<Account, BigDecimal> accountBalances,
        BigDecimal accountTotal,
        BigDecimal outstandingBorrowed,
        BigDecimal outstandingLent,
        BigDecimal netWorth,
        RecurringCommitmentSummary recurringCommitments,
        List<BudgetPlanStatus> customBudgetPerformance) {

    public PortfolioAnalyticsSnapshot {
        Objects.requireNonNull(transactionReport);
        accountBalances = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(accountBalances)));
        Objects.requireNonNull(accountTotal);
        Objects.requireNonNull(outstandingBorrowed);
        Objects.requireNonNull(outstandingLent);
        Objects.requireNonNull(netWorth);
        Objects.requireNonNull(recurringCommitments);
        customBudgetPerformance = List.copyOf(
                Objects.requireNonNull(customBudgetPerformance));
    }
}
