package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.Transfer;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FinancialReportingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final AccountService accountService;
    private final BudgetService budgetService;

    public FinancialReportingService(
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            AccountService accountService,
            BudgetService budgetService) {
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.budgetService = Objects.requireNonNull(
                budgetService, "Budget service is required.");
    }

    public CalendarMonthSnapshot buildCalendarMonth(YearMonth month) {
        YearMonth requiredMonth = Objects.requireNonNull(
                month, "Calendar month is required.");
        LinkedHashMap<LocalDate, DayAccumulator> accumulators =
                new LinkedHashMap<>();
        for (int day = 1; day <= requiredMonth.lengthOfMonth(); day++) {
            LocalDate date = requiredMonth.atDay(day);
            accumulators.put(date, new DayAccumulator(date));
        }

        for (Expense expense : expenseService.getAllExpenses()) {
            DayAccumulator day = accumulators.get(expense.getDate());
            if (day != null) {
                day.addExpense(expense);
            }
        }
        for (Income income : incomeService.getAllIncome()) {
            DayAccumulator day = accumulators.get(income.getDate());
            if (day != null) {
                day.addIncome(income);
            }
        }
        for (Transfer transfer : transferService.getAllTransfers()) {
            DayAccumulator day = accumulators.get(transfer.getDate());
            if (day != null) {
                day.addTransfer(transfer);
            }
        }

        LinkedHashMap<LocalDate, DailyActivitySnapshot> days =
                new LinkedHashMap<>();
        for (Map.Entry<LocalDate, DayAccumulator> entry
                : accumulators.entrySet()) {
            days.put(entry.getKey(), entry.getValue().snapshot());
        }
        int sundayBasedColumn = requiredMonth.atDay(1)
                .getDayOfWeek().getValue() % 7;
        return new CalendarMonthSnapshot(
                requiredMonth, sundayBasedColumn, days);
    }

    public AdvancedReportSnapshot buildAdvancedReport(
            LocalDate startDate,
            LocalDate endDate,
            Account accountFilter,
            Category categoryFilter) {
        LocalDate requiredStart = Objects.requireNonNull(
                startDate, "Report start date is required.");
        LocalDate requiredEnd = Objects.requireNonNull(
                endDate, "Report end date is required.");
        if (requiredStart.isAfter(requiredEnd)) {
            throw new ValidationException(
                    "Report start date must not be after end date.");
        }

        List<Expense> expenses = expenseService.getAllExpenses().stream()
                .filter(expense -> inRange(
                    expense.getDate(), requiredStart, requiredEnd))
                .filter(expense -> accountFilter == null
                    || expense.getAccount().equals(accountFilter))
                .filter(expense -> categoryFilter == null
                    || expense.getCategory().equals(categoryFilter))
                .toList();
        List<Income> incomeEntries = incomeService.getAllIncome().stream()
                .filter(income -> inRange(
                    income.getDate(), requiredStart, requiredEnd))
                .filter(income -> accountFilter == null
                    || income.getAccount().equals(accountFilter))
                .toList();
        List<Transfer> transfers = transferService.getAllTransfers().stream()
                .filter(transfer -> inRange(
                    transfer.getDate(), requiredStart, requiredEnd))
                .filter(transfer -> accountFilter == null
                    || transfer.getSourceAccount().equals(accountFilter)
                    || transfer.getDestinationAccount().equals(accountFilter))
                .toList();

        BigDecimal totalExpenses = sumExpenses(expenses);
        BigDecimal totalIncome = sumIncome(incomeEntries);
        LinkedHashMap<Category, BigDecimal> expensesByCategory =
                groupExpensesByCategory(expenses);
        LinkedHashMap<String, BigDecimal> incomeBySource =
                groupIncomeBySource(incomeEntries);
        LinkedHashMap<Account, AccountActivitySummary> accountActivity =
                buildAccountActivity(
                        expenses,
                        incomeEntries,
                        transfers,
                        accountFilter);
        LinkedHashMap<YearMonth, MonthlyCashFlowSummary> trend =
                buildMonthlyTrend(
                        requiredStart, requiredEnd, expenses, incomeEntries);
        List<Category> highestCategories = expensesByCategory.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue()
                        .reversed()
                        .thenComparing(entry -> entry.getKey().getDisplayName(),
                                String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();
        List<BudgetActualSummary> budgetActuals = buildBudgetActuals(
                requiredStart, requiredEnd, expenses);

        return new AdvancedReportSnapshot(
                requiredStart,
                requiredEnd,
                totalIncome,
                totalExpenses,
                expensesByCategory,
                incomeBySource,
                accountActivity,
                trend,
                highestCategories,
                budgetActuals);
    }

    private LinkedHashMap<Account, AccountActivitySummary> buildAccountActivity(
            List<Expense> expenses,
            List<Income> incomeEntries,
            List<Transfer> transfers,
            Account accountFilter) {
        List<Account> accounts = accountFilter == null
                ? accountService.listAllAccounts()
                : List.of(accountFilter);
        LinkedHashMap<Account, AccountAccumulator> accumulators =
                new LinkedHashMap<>();
        for (Account account : accounts) {
            accumulators.put(account, new AccountAccumulator(account));
        }
        for (Expense expense : expenses) {
            AccountAccumulator accumulator =
                    accumulators.get(expense.getAccount());
            if (accumulator != null) {
                accumulator.expenses =
                        accumulator.expenses.add(expense.getAmount());
            }
        }
        for (Income income : incomeEntries) {
            AccountAccumulator accumulator =
                    accumulators.get(income.getAccount());
            if (accumulator != null) {
                accumulator.income = accumulator.income.add(income.getAmount());
            }
        }
        for (Transfer transfer : transfers) {
            AccountAccumulator source =
                    accumulators.get(transfer.getSourceAccount());
            if (source != null) {
                source.outgoing = source.outgoing.add(transfer.getAmount());
            }
            AccountAccumulator destination =
                    accumulators.get(transfer.getDestinationAccount());
            if (destination != null) {
                destination.incoming =
                        destination.incoming.add(transfer.getAmount());
            }
        }
        LinkedHashMap<Account, AccountActivitySummary> summaries =
                new LinkedHashMap<>();
        for (AccountAccumulator accumulator : accumulators.values()) {
            summaries.put(accumulator.account, accumulator.snapshot());
        }
        return summaries;
    }

    private static LinkedHashMap<YearMonth, MonthlyCashFlowSummary>
            buildMonthlyTrend(
                    LocalDate startDate,
                    LocalDate endDate,
                    List<Expense> expenses,
                    List<Income> incomeEntries) {
        LinkedHashMap<YearMonth, BigDecimal> incomeByMonth =
                new LinkedHashMap<>();
        LinkedHashMap<YearMonth, BigDecimal> expensesByMonth =
                new LinkedHashMap<>();
        YearMonth current = YearMonth.from(startDate);
        YearMonth last = YearMonth.from(endDate);
        while (!current.isAfter(last)) {
            incomeByMonth.put(current, ZERO);
            expensesByMonth.put(current, ZERO);
            current = current.plusMonths(1);
        }
        for (Expense expense : expenses) {
            YearMonth month = YearMonth.from(expense.getDate());
            expensesByMonth.put(
                    month, expensesByMonth.get(month).add(expense.getAmount()));
        }
        for (Income income : incomeEntries) {
            YearMonth month = YearMonth.from(income.getDate());
            incomeByMonth.put(
                    month, incomeByMonth.get(month).add(income.getAmount()));
        }
        LinkedHashMap<YearMonth, MonthlyCashFlowSummary> trend =
                new LinkedHashMap<>();
        for (YearMonth month : incomeByMonth.keySet()) {
            trend.put(month, new MonthlyCashFlowSummary(
                    month, incomeByMonth.get(month), expensesByMonth.get(month)));
        }
        return trend;
    }

    private List<BudgetActualSummary> buildBudgetActuals(
            LocalDate startDate,
            LocalDate endDate,
            List<Expense> expenses) {
        List<BudgetActualSummary> summaries = new ArrayList<>();
        YearMonth current = YearMonth.from(startDate);
        YearMonth last = YearMonth.from(endDate);
        while (!current.isAfter(last)) {
            MonthlyBudget budget = budgetService.getBudget(current);
            if (budget.hasAnyLimit()) {
                final YearMonth reportMonth = current;
                List<Expense> monthExpenses = expenses.stream()
                        .filter(expense -> YearMonth.from(expense.getDate())
                            .equals(reportMonth))
                        .toList();
                LinkedHashMap<Category, BigDecimal> monthActuals =
                        groupExpensesByCategory(monthExpenses);
                summaries.add(new BudgetActualSummary(
                        current,
                        budget.getOverallLimit(),
                        sumExpenses(monthExpenses),
                        budget.getCategoryLimits(),
                        monthActuals));
            }
            current = current.plusMonths(1);
        }
        return List.copyOf(summaries);
    }

    private static LinkedHashMap<Category, BigDecimal> groupExpensesByCategory(
            List<Expense> expenses) {
        LinkedHashMap<Category, BigDecimal> totals = new LinkedHashMap<>();
        expenses.stream()
                .map(Expense::getCategory)
                .distinct()
                .sorted()
                .forEach(category -> totals.put(category, ZERO));
        for (Expense expense : expenses) {
            totals.merge(
                    expense.getCategory(), expense.getAmount(), BigDecimal::add);
        }
        return totals;
    }

    private static LinkedHashMap<String, BigDecimal> groupIncomeBySource(
            List<Income> incomeEntries) {
        LinkedHashMap<String, BigDecimal> totals = new LinkedHashMap<>();
        incomeEntries.stream()
                .map(Income::getSource)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(source -> totals.put(source, ZERO));
        for (Income income : incomeEntries) {
            totals.merge(income.getSource(), income.getAmount(), BigDecimal::add);
        }
        return totals;
    }

    private static BigDecimal sumExpenses(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal sumIncome(List<Income> incomeEntries) {
        return incomeEntries.stream()
                .map(Income::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private static boolean inRange(
            LocalDate date, LocalDate startDate, LocalDate endDate) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private static final class DayAccumulator {

        private final LocalDate date;
        private BigDecimal expenses = ZERO;
        private BigDecimal income = ZERO;
        private final List<FinancialActivityEntry> entries = new ArrayList<>();

        private DayAccumulator(LocalDate date) {
            this.date = date;
        }

        private void addExpense(Expense expense) {
            expenses = expenses.add(expense.getAmount());
            entries.add(new FinancialActivityEntry(
                    FinancialEntryType.EXPENSE,
                    expense.getDate(),
                    expense.getAmount(),
                    expense.getAccount(),
                    null,
                    expense.getCategory(),
                    expense.getDescription()));
        }

        private void addIncome(Income incomeEntry) {
            income = income.add(incomeEntry.getAmount());
            entries.add(new FinancialActivityEntry(
                    FinancialEntryType.INCOME,
                    incomeEntry.getDate(),
                    incomeEntry.getAmount(),
                    incomeEntry.getAccount(),
                    null,
                    null,
                    incomeEntry.getSource()));
        }

        private void addTransfer(Transfer transfer) {
            String description = transfer.getNote().isBlank()
                    ? "Transfer"
                    : transfer.getNote();
            entries.add(new FinancialActivityEntry(
                    FinancialEntryType.TRANSFER,
                    transfer.getDate(),
                    transfer.getAmount(),
                    transfer.getSourceAccount(),
                    transfer.getDestinationAccount(),
                    null,
                    description));
        }

        private DailyActivitySnapshot snapshot() {
            entries.sort(Comparator
                    .comparing(FinancialActivityEntry::getType)
                    .thenComparing(FinancialActivityEntry::getDescription,
                            String.CASE_INSENSITIVE_ORDER));
            return new DailyActivitySnapshot(date, expenses, income, entries);
        }
    }

    private static final class AccountAccumulator {

        private final Account account;
        private BigDecimal income = ZERO;
        private BigDecimal expenses = ZERO;
        private BigDecimal incoming = ZERO;
        private BigDecimal outgoing = ZERO;

        private AccountAccumulator(Account account) {
            this.account = account;
        }

        private AccountActivitySummary snapshot() {
            return new AccountActivitySummary(
                    account, income, expenses, incoming, outgoing);
        }
    }
}
