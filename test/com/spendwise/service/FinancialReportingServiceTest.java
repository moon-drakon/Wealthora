package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.Transfer;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.repository.TransferRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FinancialReportingServiceTest {

    private static final Account BANK = Account.createCustom(
            "ACCOUNT_REPORT_BANK",
            "Report Bank",
            AccountType.BANK,
            new BigDecimal("10.00"),
            false);
    private static int passed;

    private FinancialReportingServiceTest() {
    }

    public static void main(String[] args) {
        test("calendar Sunday-based alignment", FinancialReportingServiceTest::alignment);
        test("calendar leap year", FinancialReportingServiceTest::leapYear);
        test("daily income total", FinancialReportingServiceTest::dailyIncome);
        test("daily expense and net totals", FinancialReportingServiceTest::dailyExpenseNet);
        test("transfers appear but are excluded", FinancialReportingServiceTest::transferExclusion);
        test("day details preserve fields", FinancialReportingServiceTest::dayDetails);
        test("empty calendar month", FinancialReportingServiceTest::emptyMonth);
        test("report date validation", FinancialReportingServiceTest::dateValidation);
        test("income versus expense summary", FinancialReportingServiceTest::incomeExpenseSummary);
        test("expense category grouping and ranking", FinancialReportingServiceTest::categoryGrouping);
        test("income source grouping", FinancialReportingServiceTest::sourceGrouping);
        test("account activity grouping", FinancialReportingServiceTest::accountGrouping);
        test("monthly trend includes gaps", FinancialReportingServiceTest::monthlyTrend);
        test("budget versus actual", FinancialReportingServiceTest::budgetActual);
        test("account and category filters", FinancialReportingServiceTest::filters);
        test("reporting remains read only", FinancialReportingServiceTest::readOnly);
        test("report snapshots are immutable", FinancialReportingServiceTest::immutableSnapshots);
        System.out.println(
                "All " + passed + " financial reporting service tests passed.");
    }

    private static void alignment() {
        CalendarMonthSnapshot january = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 1));
        CalendarMonthSnapshot september = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 9));
        assertEquals(1, january.getFirstDayColumn());
        assertEquals(0, september.getFirstDayColumn());
    }

    private static void leapYear() {
        CalendarMonthSnapshot february = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 2));
        assertEquals(29, february.getDays().size());
        assertEquals(LocalDate.of(2024, 2, 29),
                february.getDays().keySet().stream().reduce((a, b) -> b)
                        .orElseThrow());
    }

    private static void dailyIncome() {
        DailyActivitySnapshot day = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 1))
                .getDay(LocalDate.of(2024, 1, 2));
        assertMoney("100.00", day.getIncomeTotal());
    }

    private static void dailyExpenseNet() {
        DailyActivitySnapshot day = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 1))
                .getDay(LocalDate.of(2024, 1, 2));
        assertMoney("30.00", day.getExpenseTotal());
        assertMoney("70.00", day.getNetCashFlow());
    }

    private static void transferExclusion() {
        DailyActivitySnapshot day = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 1))
                .getDay(LocalDate.of(2024, 1, 2));
        assertEquals(3, day.getEntries().size());
        assertTrue(day.getEntries().stream().anyMatch(
                entry -> entry.getType() == FinancialEntryType.TRANSFER));
        assertMoney("100.00", day.getIncomeTotal());
        assertMoney("30.00", day.getExpenseTotal());
    }

    private static void dayDetails() {
        DailyActivitySnapshot day = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 1))
                .getDay(LocalDate.of(2024, 1, 2));
        FinancialActivityEntry expense = day.getEntries().stream()
                .filter(entry -> entry.getType() == FinancialEntryType.EXPENSE)
                .findFirst()
                .orElseThrow();
        assertEquals("Lunch", expense.getDescription());
        assertEquals(Category.FOOD, expense.getCategory().orElseThrow());
        assertEquals(Account.DEFAULT, expense.getAccount());
    }

    private static void emptyMonth() {
        CalendarMonthSnapshot march = fixture().service
                .buildCalendarMonth(YearMonth.of(2024, 3));
        assertFalse(march.hasActivity());
        assertEquals(31, march.getDays().size());
    }

    private static void dateValidation() {
        Fixture fixture = fixture();
        expect(ValidationException.class, () ->
            fixture.service.buildAdvancedReport(
                    LocalDate.of(2024, 2, 1),
                    LocalDate.of(2024, 1, 1),
                    null,
                    null));
    }

    private static void incomeExpenseSummary() {
        AdvancedReportSnapshot report = fullReport();
        assertMoney("150.00", report.getTotalIncome());
        assertMoney("50.00", report.getTotalExpenses());
        assertMoney("100.00", report.getNetCashFlow());
    }

    private static void categoryGrouping() {
        AdvancedReportSnapshot report = fullReport();
        assertMoney("30.00", report.getExpensesByCategory().get(Category.FOOD));
        assertMoney("20.00", report.getExpensesByCategory().get(Category.BILLS));
        assertEquals(List.of(Category.FOOD, Category.BILLS),
                report.getHighestExpenseCategories());
    }

    private static void sourceGrouping() {
        AdvancedReportSnapshot report = fullReport();
        assertMoney("100.00", report.getIncomeBySource().get("Salary"));
        assertMoney("50.00", report.getIncomeBySource().get("Bonus"));
    }

    private static void accountGrouping() {
        AdvancedReportSnapshot report = fullReport();
        AccountActivitySummary cash =
                report.getAccountActivity().get(Account.DEFAULT);
        AccountActivitySummary bank = report.getAccountActivity().get(BANK);
        assertMoney("100.00", cash.getIncome());
        assertMoney("30.00", cash.getExpenses());
        assertMoney("40.00", cash.getOutgoingTransfers());
        assertMoney("40.00", bank.getIncomingTransfers());
        assertMoney("70.00", bank.getNetActivity());
    }

    private static void monthlyTrend() {
        AdvancedReportSnapshot report = fixture().service.buildAdvancedReport(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31),
                null,
                null);
        assertEquals(3, report.getMonthlyTrend().size());
        assertMoney("30.00", report.getMonthlyTrend()
                .get(YearMonth.of(2024, 1)).getExpenses());
        assertMoney("0.00", report.getMonthlyTrend()
                .get(YearMonth.of(2024, 3)).getIncome());
    }

    private static void budgetActual() {
        AdvancedReportSnapshot report = fullReport();
        assertEquals(1, report.getBudgetActuals().size());
        BudgetActualSummary january = report.getBudgetActuals().get(0);
        assertEquals(YearMonth.of(2024, 1), january.getMonth());
        assertMoney("200.00", january.getOverallLimit().orElseThrow());
        assertMoney("30.00", january.getActualExpenses());
        assertMoney("30.00", january.getCategoryActuals().get(Category.FOOD));
    }

    private static void filters() {
        AdvancedReportSnapshot report = fixture().service.buildAdvancedReport(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 2, 29),
                BANK,
                Category.BILLS);
        assertMoney("50.00", report.getTotalIncome());
        assertMoney("20.00", report.getTotalExpenses());
        assertEquals(1, report.getAccountActivity().size());
        assertEquals(BANK, report.getAccountActivity().keySet().iterator().next());
    }

    private static void readOnly() {
        Fixture fixture = fixture();
        List<Expense> expenses = List.copyOf(fixture.expenses.entries);
        List<Income> income = List.copyOf(fixture.income.entries);
        List<Transfer> transfers = List.copyOf(fixture.transfers.entries);
        Map<YearMonth, MonthlyBudget> budgets = Map.copyOf(fixture.budgets.saved);
        fixture.service.buildCalendarMonth(YearMonth.of(2024, 1));
        fixture.service.buildAdvancedReport(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 2, 29),
                null,
                null);
        assertEquals(expenses, fixture.expenses.entries);
        assertEquals(income, fixture.income.entries);
        assertEquals(transfers, fixture.transfers.entries);
        assertEquals(budgets, fixture.budgets.saved);
        assertEquals(0, fixture.totalMutations());
    }

    private static void immutableSnapshots() {
        AdvancedReportSnapshot report = fullReport();
        expect(UnsupportedOperationException.class,
                () -> report.getExpensesByCategory().clear());
        expect(UnsupportedOperationException.class,
                () -> report.getMonthlyTrend().clear());
        expect(UnsupportedOperationException.class,
                () -> report.getBudgetActuals().clear());
    }

    private static AdvancedReportSnapshot fullReport() {
        return fixture().service.buildAdvancedReport(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 2, 29),
                null,
                null);
    }

    private static Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.accounts.entries.add(BANK);
        fixture.expenses.entries.add(new Expense(
                "report-expense-food",
                "Lunch",
                new BigDecimal("30.00"),
                LocalDate.of(2024, 1, 2),
                Category.FOOD,
                Account.DEFAULT,
                ""));
        fixture.expenses.entries.add(new Expense(
                "report-expense-bills",
                "Internet",
                new BigDecimal("20.00"),
                LocalDate.of(2024, 2, 10),
                Category.BILLS,
                BANK,
                ""));
        fixture.income.entries.add(new Income(
                "INCOME_REPORT_SALARY",
                LocalDate.of(2024, 1, 2),
                new BigDecimal("100.00"),
                "Salary",
                Account.DEFAULT,
                ""));
        fixture.income.entries.add(new Income(
                "INCOME_REPORT_BONUS",
                LocalDate.of(2024, 2, 10),
                new BigDecimal("50.00"),
                "Bonus",
                BANK,
                ""));
        fixture.transfers.entries.add(new Transfer(
                "TRANSFER_REPORT_MOVE",
                LocalDate.of(2024, 1, 2),
                new BigDecimal("40.00"),
                Account.DEFAULT,
                BANK,
                "Move savings"));
        fixture.budgets.saved.put(
                YearMonth.of(2024, 1),
                new MonthlyBudget(
                        YearMonth.of(2024, 1),
                        Optional.of(new BigDecimal("200.00")),
                        Map.of(Category.FOOD, new BigDecimal("50.00"))));
        return fixture;
    }

    private static final class Fixture {

        private final MemoryAccountRepository accounts =
                new MemoryAccountRepository();
        private final MemoryExpenseRepository expenses =
                new MemoryExpenseRepository();
        private final MemoryIncomeRepository income =
                new MemoryIncomeRepository();
        private final MemoryTransferRepository transfers =
                new MemoryTransferRepository();
        private final MemoryBudgetRepository budgets =
                new MemoryBudgetRepository();
        private final AccountService accountService =
                new AccountService(accounts);
        private final FinancialReportingService service =
                new FinancialReportingService(
                        new ExpenseService(expenses),
                        new IncomeService(income, accountService),
                        new TransferService(transfers, accountService),
                        accountService,
                        new BudgetService(budgets));

        private int totalMutations() {
            return accounts.mutations + expenses.mutations
                    + income.mutations + transfers.mutations
                    + budgets.mutations;
        }
    }

    private static final class MemoryAccountRepository
            implements AccountRepository {

        private final List<Account> entries = new ArrayList<>();
        private int mutations;

        @Override
        public List<Account> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Account> findById(String identifier) {
            return entries.stream().filter(account ->
                account.getIdentifier().equals(identifier)).findFirst();
        }

        @Override
        public void add(Account account) {
            mutations++;
            entries.add(account);
        }

        @Override
        public void update(Account account) {
            mutations++;
            entries.set(entries.indexOf(account), account);
        }
    }

    private static final class MemoryExpenseRepository
            implements ExpenseRepository {

        private final List<Expense> entries = new ArrayList<>();
        private int mutations;

        @Override
        public List<Expense> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Expense> findById(String id) {
            return entries.stream().filter(entry ->
                entry.getId().equals(id)).findFirst();
        }

        @Override
        public void add(Expense expense) {
            mutations++;
            entries.add(expense);
        }

        @Override
        public void update(Expense expense) {
            mutations++;
            entries.set(entries.indexOf(expense), expense);
        }

        @Override
        public boolean deleteById(String id) {
            mutations++;
            return entries.removeIf(entry -> entry.getId().equals(id));
        }
    }

    private static final class MemoryIncomeRepository
            implements IncomeRepository {

        private final List<Income> entries = new ArrayList<>();
        private int mutations;

        @Override
        public List<Income> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Income> findById(String id) {
            return entries.stream().filter(entry ->
                entry.getId().equals(id)).findFirst();
        }

        @Override
        public void add(Income entry) {
            mutations++;
            entries.add(entry);
        }

        @Override
        public void update(Income entry) {
            mutations++;
            entries.set(entries.indexOf(entry), entry);
        }

        @Override
        public boolean deleteById(String id) {
            mutations++;
            return entries.removeIf(entry -> entry.getId().equals(id));
        }
    }

    private static final class MemoryTransferRepository
            implements TransferRepository {

        private final List<Transfer> entries = new ArrayList<>();
        private int mutations;

        @Override
        public List<Transfer> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Transfer> findById(String id) {
            return entries.stream().filter(entry ->
                entry.getId().equals(id)).findFirst();
        }

        @Override
        public void add(Transfer entry) {
            mutations++;
            entries.add(entry);
        }

        @Override
        public void update(Transfer entry) {
            mutations++;
            entries.set(entries.indexOf(entry), entry);
        }

        @Override
        public boolean deleteById(String id) {
            mutations++;
            return entries.removeIf(entry -> entry.getId().equals(id));
        }
    }

    private static final class MemoryBudgetRepository
            implements BudgetRepository {

        private final Map<YearMonth, MonthlyBudget> saved =
                new LinkedHashMap<>();
        private int mutations;

        @Override
        public Optional<MonthlyBudget> findByMonth(YearMonth month) {
            return Optional.ofNullable(saved.get(month));
        }

        @Override
        public void save(MonthlyBudget budget) {
            mutations++;
            saved.put(budget.getMonth(), budget);
        }

        @Override
        public boolean delete(YearMonth month) {
            mutations++;
            return saved.remove(month) != null;
        }

        @Override
        public boolean isCategoryReferenced(Category category) {
            return saved.values().stream().anyMatch(budget ->
                budget.getCategoryLimits().containsKey(category));
        }
    }

    private static void test(String name, Runnable action) {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
        assertEquals(2, actual.scale());
    }

    private static <T extends Throwable> void expect(
            Class<T> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(
                    "Expected " + expected.getSimpleName()
                    + " but caught " + actual, actual);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + ".");
    }
}
