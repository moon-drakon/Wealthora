package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.DebtDirection;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class PortfolioAnalyticsService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private final FinancialReportingService reportingService;
    private final FinanceService financeService;
    private final RecurringService recurringService;
    private final AdvancedBudgetService budgetService;
    private final DebtService debtService;

    public PortfolioAnalyticsService(
            FinancialReportingService reportingService,
            FinanceService financeService,
            RecurringService recurringService,
            AdvancedBudgetService budgetService,
            DebtService debtService) {
        this.reportingService = Objects.requireNonNull(reportingService);
        this.financeService = Objects.requireNonNull(financeService);
        this.recurringService = Objects.requireNonNull(recurringService);
        this.budgetService = Objects.requireNonNull(budgetService);
        this.debtService = Objects.requireNonNull(debtService);
    }

    public PortfolioAnalyticsSnapshot build(
            LocalDate startDate, LocalDate endDate,
            Account accountFilter, Category categoryFilter,
            LocalDate statusDate) {
        AdvancedReportSnapshot report = reportingService.buildAdvancedReport(
                startDate, endDate, accountFilter, categoryFilter);
        AccountBalanceSnapshot balances = financeService.calculateBalances();
        LinkedHashMap<Account, BigDecimal> accountBalances =
                new LinkedHashMap<>(balances.getBalances());
        BigDecimal borrowed = debtService.listProgress(statusDate).stream()
                .filter(item -> item.debt().getDirection()
                        == DebtDirection.BORROWED)
                .map(DebtProgress::remainingAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal lent = debtService.listProgress(statusDate).stream()
                .filter(item -> item.debt().getDirection() == DebtDirection.LENT)
                .map(DebtProgress::remainingAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal netWorth = balances.getTotalBalance()
                .add(lent).subtract(borrowed);
        List<BudgetPlanStatus> budgetPerformance = budgetService.listHistory()
                .stream()
                .filter(plan -> !plan.getEndDate().isBefore(startDate)
                        && !plan.getStartDate().isAfter(endDate))
                .map(plan -> budgetService.evaluate(plan.getIdentifier()))
                .toList();
        return new PortfolioAnalyticsSnapshot(report, accountBalances,
                balances.getTotalBalance(), borrowed, lent, netWorth,
                commitments(), budgetPerformance);
    }

    private RecurringCommitmentSummary commitments() {
        BigDecimal income = ZERO;
        BigDecimal expenses = ZERO;
        BigDecimal transfers = ZERO;
        BigDecimal bills = ZERO;
        BigDecimal subscriptions = ZERO;
        for (RecurringEntry entry : recurringService.listAll()) {
            if (!entry.isActive()) continue;
            if (entry.getType() == RecurringEntryType.INCOME) {
                income = income.add(entry.getAmount());
            } else if (entry.getType() == RecurringEntryType.TRANSFER) {
                transfers = transfers.add(entry.getAmount());
            } else {
                expenses = expenses.add(entry.getAmount());
                if (entry.getKind() == RecurringKind.BILL) {
                    bills = bills.add(entry.getAmount());
                } else if (entry.getKind() == RecurringKind.SUBSCRIPTION) {
                    subscriptions = subscriptions.add(entry.getAmount());
                }
            }
        }
        return new RecurringCommitmentSummary(
                income, expenses, transfers, bills, subscriptions);
    }
}
