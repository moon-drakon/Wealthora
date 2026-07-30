package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ExpenseAnalyticsServiceTest {

    private static final YearMonth SELECTED_MONTH = YearMonth.of(2024, 6);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("null expense service", ExpenseAnalyticsServiceTest::nullServiceIsRejected);
        runTest("null selected month", ExpenseAnalyticsServiceTest::nullMonthIsRejected);
        runTest("zero trend count", ExpenseAnalyticsServiceTest::zeroTrendCountIsRejected);
        runTest("negative trend count", ExpenseAnalyticsServiceTest::negativeTrendCountIsRejected);
        runTest("trend above maximum", ExpenseAnalyticsServiceTest::largeTrendCountIsRejected);
        runTest("one trend month", ExpenseAnalyticsServiceTest::oneTrendMonthIsAccepted);
        runTest("twelve trend months", ExpenseAnalyticsServiceTest::twelveTrendMonthsAreAccepted);
        runTest("default six months", ExpenseAnalyticsServiceTest::defaultOverloadUsesSixMonths);
        runTest("empty selected count", ExpenseAnalyticsServiceTest::emptyMonthHasZeroCount);
        runTest("empty selected total", ExpenseAnalyticsServiceTest::emptyMonthHasZeroTotal);
        runTest("empty selected average", ExpenseAnalyticsServiceTest::emptyMonthHasZeroAverage);
        runTest("matching selected expenses", ExpenseAnalyticsServiceTest::matchingExpensesAreIncluded);
        runTest("earlier expense exclusion", ExpenseAnalyticsServiceTest::earlierExpensesAreExcluded);
        runTest("later expense exclusion", ExpenseAnalyticsServiceTest::laterExpensesAreExcluded);
        runTest("first month date", ExpenseAnalyticsServiceTest::firstDateIsIncluded);
        runTest("last month date", ExpenseAnalyticsServiceTest::lastDateIsIncluded);
        runTest("selected order", ExpenseAnalyticsServiceTest::selectedOrderIsPreserved);
        runTest("selected list immutable", ExpenseAnalyticsServiceTest::selectedListIsUnmodifiable);
        runTest("selected summary count", ExpenseAnalyticsServiceTest::selectedSummaryCountIsCorrect);
        runTest("selected exact total", ExpenseAnalyticsServiceTest::selectedTotalIsExact);
        runTest("selected rounded average", ExpenseAnalyticsServiceTest::selectedAverageUsesHalfUp);
        runTest("selected category totals", ExpenseAnalyticsServiceTest::categoryTotalsAreCorrect);
        runTest("all categories retained", ExpenseAnalyticsServiceTest::everyCategoryIsAvailable);
        runTest("previous total", ExpenseAnalyticsServiceTest::previousMonthTotalIsCorrect);
        runTest("missing previous total", ExpenseAnalyticsServiceTest::missingPreviousMonthIsZero);
        runTest("positive change", ExpenseAnalyticsServiceTest::positiveChangeIsCorrect);
        runTest("negative change", ExpenseAnalyticsServiceTest::negativeChangeIsCorrect);
        runTest("zero change", ExpenseAnalyticsServiceTest::zeroChangeIsCorrect);
        runTest("exact trend count", ExpenseAnalyticsServiceTest::trendHasExactCount);
        runTest("trend first month", ExpenseAnalyticsServiceTest::trendStartsCorrectly);
        runTest("trend final month", ExpenseAnalyticsServiceTest::trendEndsCorrectly);
        runTest("trend chronological order", ExpenseAnalyticsServiceTest::trendIsChronological);
        runTest("missing trend months", ExpenseAnalyticsServiceTest::missingTrendMonthsAreZero);
        runTest("trend exact totals", ExpenseAnalyticsServiceTest::trendTotalsAreCorrect);
        runTest("outside trend ignored", ExpenseAnalyticsServiceTest::outsideTrendIsIgnored);
        runTest("trend map immutable", ExpenseAnalyticsServiceTest::monthlyMapIsUnmodifiable);
        runTest("expense-list defensive copy", ExpenseAnalyticsServiceTest::snapshotCopiesExpenseList);
        runTest("monthly-map defensive copy", ExpenseAnalyticsServiceTest::snapshotCopiesMonthlyMap);
        runTest("null expense element", ExpenseAnalyticsServiceTest::snapshotRejectsNullExpense);
        runTest("null month key", ExpenseAnalyticsServiceTest::snapshotRejectsNullMonthKey);
        runTest("null money value", ExpenseAnalyticsServiceTest::snapshotRejectsNullMoneyValue);
        runTest("two-decimal money", ExpenseAnalyticsServiceTest::snapshotMoneyUsesTwoDecimals);
        runTest("one repository snapshot", ExpenseAnalyticsServiceTest::analysisLoadsOnce);
        runTest("expense mutation safety", ExpenseAnalyticsServiceTest::analysisDoesNotMutateExpenses);
        runTest("repository order safety", ExpenseAnalyticsServiceTest::analysisPreservesRepositoryOrder);
        runTest("repository exception propagation",
                ExpenseAnalyticsServiceTest::repositoryExceptionIsNotSwallowed);
        runTest("read-only analysis", ExpenseAnalyticsServiceTest::analysisDoesNotChangeRepository);
        runTest("no analytics cache", ExpenseAnalyticsServiceTest::repeatedAnalysisUsesCurrentData);

        System.out.println("All " + passedTests + " analytics service tests passed.");
    }

    private static void nullServiceIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new ExpenseAnalyticsService(null),
                "Null ExpenseService should be rejected.");
    }

    private static void nullMonthIsRejected() {
        expectThrows(
                ValidationException.class,
                () -> analyticsService(new InMemoryExpenseRepository()).analyzeMonth(null),
                "Null selected month should be rejected.");
    }

    private static void zeroTrendCountIsRejected() {
        expectInvalidTrendCount(0);
    }

    private static void negativeTrendCountIsRejected() {
        expectInvalidTrendCount(-1);
    }

    private static void largeTrendCountIsRejected() {
        expectInvalidTrendCount(13);
    }

    private static void oneTrendMonthIsAccepted() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 1);

        assertEquals(
                List.of(SELECTED_MONTH),
                List.copyOf(snapshot.getMonthlyTotals().keySet()),
                "One-month trend should contain only the selected month.");
    }

    private static void twelveTrendMonthsAreAccepted() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 12);

        assertEquals(12, snapshot.getMonthlyTotals().size(),
                "Twelve-month trend was not accepted.");
    }

    private static void defaultOverloadUsesSixMonths() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH);

        assertEquals(6, snapshot.getMonthlyTotals().size(),
                "Default overload should use exactly six months.");
    }

    private static void emptyMonthHasZeroCount() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH);

        assertEquals(0, snapshot.getSelectedMonthSummary().getExpenseCount(),
                "Empty selected month count should be zero.");
    }

    private static void emptyMonthHasZeroTotal() {
        assertMoney(
                "0.00",
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthSummary()
                        .getTotalAmount(),
                "Empty selected month total should be 0.00.");
    }

    private static void emptyMonthHasZeroAverage() {
        assertMoney(
                "0.00",
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthSummary()
                        .getAverageAmount(),
                "Empty selected month average should be 0.00.");
    }

    private static void matchingExpensesAreIncluded() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense("selected", SELECTED_MONTH.atDay(10), "12.50", Category.FOOD));

        assertIds(
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthExpenses(),
                List.of("selected"),
                "Matching selected-month expense was not included.");
    }

    private static void earlierExpensesAreExcluded() {
        InMemoryExpenseRepository repository = repositoryWithAdjacentMonths();

        assertFalse(
                ids(analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthExpenses())
                        .contains("earlier"),
                "Earlier-month expense was included.");
    }

    private static void laterExpensesAreExcluded() {
        InMemoryExpenseRepository repository = repositoryWithAdjacentMonths();

        assertFalse(
                ids(analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthExpenses())
                        .contains("later"),
                "Later-month expense was included.");
    }

    private static void firstDateIsIncluded() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "first-date", SELECTED_MONTH.atDay(1), "1.00", Category.OTHER));

        assertIds(
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthExpenses(),
                List.of("first-date"),
                "First calendar date was not included.");
    }

    private static void lastDateIsIncluded() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "last-date", SELECTED_MONTH.atEndOfMonth(), "1.00", Category.OTHER));

        assertIds(
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthExpenses(),
                List.of("last-date"),
                "Last calendar date was not included.");
    }

    private static void selectedOrderIsPreserved() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense("third", SELECTED_MONTH.atDay(20), "3.00", Category.OTHER));
        repository.seed(expense("first", SELECTED_MONTH.atDay(1), "1.00", Category.FOOD));
        repository.seed(expense("second", SELECTED_MONTH.atDay(10), "2.00", Category.BILLS));

        assertIds(
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getSelectedMonthExpenses(),
                List.of("third", "first", "second"),
                "Selected expenses did not preserve repository order.");
    }

    private static void selectedListIsUnmodifiable() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(repositoryWithSelectedExpenses()).analyzeMonth(SELECTED_MONTH);

        expectThrows(
                UnsupportedOperationException.class,
                () -> snapshot.getSelectedMonthExpenses().clear(),
                "Selected-month list should be unmodifiable.");
    }

    private static void selectedSummaryCountIsCorrect() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(repositoryWithSelectedExpenses()).analyzeMonth(SELECTED_MONTH);

        assertEquals(3, snapshot.getSelectedMonthSummary().getExpenseCount(),
                "Selected-month summary count is incorrect.");
    }

    private static void selectedTotalIsExact() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(repositoryWithSelectedExpenses()).analyzeMonth(SELECTED_MONTH);

        assertMoney("30.02", snapshot.getSelectedMonthSummary().getTotalAmount(),
                "Selected-month total is incorrect.");
    }

    private static void selectedAverageUsesHalfUp() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(repositoryWithSelectedExpenses()).analyzeMonth(SELECTED_MONTH);

        assertMoney("10.01", snapshot.getSelectedMonthSummary().getAverageAmount(),
                "Selected-month average should preserve HALF_UP behavior.");
    }

    private static void categoryTotalsAreCorrect() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(repositoryWithSelectedExpenses()).analyzeMonth(SELECTED_MONTH);

        assertMoney(
                "20.01",
                snapshot.getSelectedMonthSummary().getTotalForCategory(Category.FOOD),
                "Selected-month food total is incorrect.");
        assertMoney(
                "10.01",
                snapshot.getSelectedMonthSummary().getTotalForCategory(Category.BILLS),
                "Selected-month bills total is incorrect.");
    }

    private static void everyCategoryIsAvailable() {
        ExpenseSummary summary = analyticsService(new InMemoryExpenseRepository())
                .analyzeMonth(SELECTED_MONTH)
                .getSelectedMonthSummary();

        assertEquals(Category.values().length, summary.getTotalsByCategory().size(),
                "Selected summary should include every category.");
        for (Category category : Category.values()) {
            assertTrue(summary.getTotalsByCategory().containsKey(category),
                    "Selected summary is missing " + category.name() + ".");
        }
    }

    private static void previousMonthTotalIsCorrect() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        YearMonth previousMonth = SELECTED_MONTH.minusMonths(1);
        repository.seed(expense(
                "previous-one", previousMonth.atDay(1), "12.25", Category.FOOD));
        repository.seed(expense(
                "previous-two", previousMonth.atEndOfMonth(), "7.75", Category.BILLS));

        assertMoney(
                "20.00",
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getPreviousMonthTotal(),
                "Previous-month total is incorrect.");
    }

    private static void missingPreviousMonthIsZero() {
        assertMoney(
                "0.00",
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH)
                        .getPreviousMonthTotal(),
                "Missing previous month should return 0.00.");
    }

    private static void positiveChangeIsCorrect() {
        InMemoryExpenseRepository repository = changeRepository("20.00", "35.00");

        assertMoney(
                "15.00",
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getChangeFromPreviousMonth(),
                "Positive month-to-month change is incorrect.");
    }

    private static void negativeChangeIsCorrect() {
        InMemoryExpenseRepository repository = changeRepository("35.00", "20.00");

        assertMoney(
                "-15.00",
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getChangeFromPreviousMonth(),
                "Negative month-to-month change is incorrect.");
    }

    private static void zeroChangeIsCorrect() {
        InMemoryExpenseRepository repository = changeRepository("20.00", "20.00");

        assertMoney(
                "0.00",
                analyticsService(repository)
                        .analyzeMonth(SELECTED_MONTH)
                        .getChangeFromPreviousMonth(),
                "Zero month-to-month change is incorrect.");
    }

    private static void trendHasExactCount() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 4);

        assertEquals(4, snapshot.getMonthlyTotals().size(),
                "Monthly trend has the wrong count.");
    }

    private static void trendStartsCorrectly() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 4);

        assertEquals(
                SELECTED_MONTH.minusMonths(3),
                snapshot.getMonthlyTotals().keySet().iterator().next(),
                "Monthly trend starts at the wrong month.");
    }

    private static void trendEndsCorrectly() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 4);
        List<YearMonth> months = List.copyOf(snapshot.getMonthlyTotals().keySet());

        assertEquals(SELECTED_MONTH, months.get(months.size() - 1),
                "Monthly trend should end at the selected month.");
    }

    private static void trendIsChronological() {
        List<YearMonth> months = List.copyOf(
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 6)
                        .getMonthlyTotals()
                        .keySet());

        for (int index = 1; index < months.size(); index++) {
            assertTrue(months.get(index).isAfter(months.get(index - 1)),
                    "Monthly trend is not chronological.");
        }
    }

    private static void missingTrendMonthsAreZero() {
        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, 6);

        for (BigDecimal total : snapshot.getMonthlyTotals().values()) {
            assertMoney("0.00", total, "Missing trend month should be 0.00.");
        }
    }

    private static void trendTotalsAreCorrect() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "march-one", YearMonth.of(2024, 3).atDay(1), "5.25", Category.FOOD));
        repository.seed(expense(
                "march-two", YearMonth.of(2024, 3).atDay(2), "4.75", Category.BILLS));
        repository.seed(expense(
                "june", SELECTED_MONTH.atDay(2), "12.50", Category.OTHER));

        Map<YearMonth, BigDecimal> totals = analyticsService(repository)
                .analyzeMonth(SELECTED_MONTH, 6)
                .getMonthlyTotals();

        assertMoney("10.00", totals.get(YearMonth.of(2024, 3)),
                "March trend total is incorrect.");
        assertMoney("12.50", totals.get(SELECTED_MONTH),
                "Selected-month trend total is incorrect.");
    }

    private static void outsideTrendIsIgnored() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        YearMonth outsideMonth = SELECTED_MONTH.minusMonths(6);
        repository.seed(expense(
                "outside", outsideMonth.atDay(1), "999.00", Category.OTHER));

        ExpenseAnalyticsSnapshot snapshot =
                analyticsService(repository).analyzeMonth(SELECTED_MONTH, 6);

        assertFalse(snapshot.getMonthlyTotals().containsKey(outsideMonth),
                "Expense outside trend range should be ignored.");
        for (BigDecimal total : snapshot.getMonthlyTotals().values()) {
            assertMoney("0.00", total, "Outside expense changed a trend total.");
        }
    }

    private static void monthlyMapIsUnmodifiable() {
        Map<YearMonth, BigDecimal> totals =
                analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH)
                        .getMonthlyTotals();

        expectThrows(
                UnsupportedOperationException.class,
                () -> totals.put(SELECTED_MONTH, BigDecimal.ONE),
                "Monthly totals map should be unmodifiable.");
    }

    private static void snapshotCopiesExpenseList() {
        List<Expense> suppliedExpenses = new ArrayList<>();
        suppliedExpenses.add(expense(
                "one", SELECTED_MONTH.atDay(1), "1.00", Category.FOOD));
        ExpenseAnalyticsSnapshot snapshot = snapshot(
                suppliedExpenses, new LinkedHashMap<>());

        suppliedExpenses.add(expense(
                "two", SELECTED_MONTH.atDay(2), "2.00", Category.FOOD));

        assertEquals(1, snapshot.getSelectedMonthExpenses().size(),
                "Snapshot retained the caller's expense list.");
    }

    private static void snapshotCopiesMonthlyMap() {
        LinkedHashMap<YearMonth, BigDecimal> suppliedTotals = new LinkedHashMap<>();
        suppliedTotals.put(SELECTED_MONTH, new BigDecimal("3.00"));
        ExpenseAnalyticsSnapshot snapshot = snapshot(List.of(), suppliedTotals);

        suppliedTotals.put(SELECTED_MONTH.plusMonths(1), new BigDecimal("4.00"));

        assertEquals(1, snapshot.getMonthlyTotals().size(),
                "Snapshot retained the caller's monthly map.");
    }

    private static void snapshotRejectsNullExpense() {
        List<Expense> expenses = Arrays.asList(
                expense("one", SELECTED_MONTH.atDay(1), "1.00", Category.FOOD),
                null);

        expectThrows(
                NullPointerException.class,
                () -> new ExpenseAnalyticsSnapshot(
                        SELECTED_MONTH,
                        summary(List.of()),
                        expenses,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new LinkedHashMap<>()),
                "Snapshot should reject null expense elements.");
    }

    private static void snapshotRejectsNullMonthKey() {
        LinkedHashMap<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        totals.put(null, new BigDecimal("1.00"));

        expectThrows(
                NullPointerException.class,
                () -> snapshot(List.of(), totals),
                "Snapshot should reject null month keys.");
    }

    private static void snapshotRejectsNullMoneyValue() {
        LinkedHashMap<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        totals.put(SELECTED_MONTH, null);

        expectThrows(
                NullPointerException.class,
                () -> snapshot(List.of(), totals),
                "Snapshot should reject null monetary values.");
    }

    private static void snapshotMoneyUsesTwoDecimals() {
        LinkedHashMap<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        totals.put(SELECTED_MONTH.minusMonths(1), BigDecimal.ZERO);
        totals.put(SELECTED_MONTH, new BigDecimal("2"));
        ExpenseAnalyticsSnapshot snapshot = new ExpenseAnalyticsSnapshot(
                SELECTED_MONTH,
                summary(List.of()),
                List.of(),
                BigDecimal.ZERO,
                new BigDecimal("-2"),
                totals);

        assertEquals(2, snapshot.getPreviousMonthTotal().scale(),
                "Previous-month total scale should be two.");
        assertEquals(2, snapshot.getChangeFromPreviousMonth().scale(),
                "Change scale should be two.");
        for (BigDecimal total : snapshot.getMonthlyTotals().values()) {
            assertEquals(2, total.scale(), "Monthly total scale should be two.");
        }
    }

    private static void analysisLoadsOnce() {
        InMemoryExpenseRepository repository = repositoryWithSelectedExpenses();
        repository.resetFindAllCalls();

        analyticsService(repository).analyzeMonth(SELECTED_MONTH);

        assertEquals(1, repository.getFindAllCalls(),
                "Analysis should load exactly one repository snapshot.");
    }

    private static void analysisDoesNotMutateExpenses() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense expense = expense(
                "unchanged", SELECTED_MONTH.atDay(5), "12.50", Category.EDUCATION);
        String originalDescription = expense.getDescription();
        BigDecimal originalAmount = expense.getAmount();
        LocalDate originalDate = expense.getDate();
        Category originalCategory = expense.getCategory();
        String originalNotes = expense.getNotes();
        repository.seed(expense);

        analyticsService(repository).analyzeMonth(SELECTED_MONTH);

        assertEquals(originalDescription, expense.getDescription(),
                "Analysis changed the description.");
        assertEquals(originalAmount, expense.getAmount(), "Analysis changed the amount.");
        assertEquals(originalDate, expense.getDate(), "Analysis changed the date.");
        assertEquals(originalCategory, expense.getCategory(), "Analysis changed the category.");
        assertEquals(originalNotes, expense.getNotes(), "Analysis changed the notes.");
    }

    private static void analysisPreservesRepositoryOrder() {
        InMemoryExpenseRepository repository = repositoryWithSelectedExpenses();
        List<String> before = ids(repository.snapshotWithoutCounting());

        analyticsService(repository).analyzeMonth(SELECTED_MONTH);

        assertEquals(before, ids(repository.snapshotWithoutCounting()),
                "Analysis altered repository order.");
    }

    private static void repositoryExceptionIsNotSwallowed() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        RepositoryException expected = new RepositoryException("Test read failure.");
        repository.failReads(expected);

        RepositoryException actual = expectThrows(
                RepositoryException.class,
                () -> analyticsService(repository).analyzeMonth(SELECTED_MONTH),
                "RepositoryException should propagate.");

        assertSame(expected, actual, "RepositoryException was replaced or wrapped.");
    }

    private static void analysisDoesNotChangeRepository() {
        InMemoryExpenseRepository repository = repositoryWithSelectedExpenses();
        int sizeBefore = repository.size();
        int mutationCountBefore = repository.getMutationCalls();

        analyticsService(repository).analyzeMonth(SELECTED_MONTH);

        assertEquals(sizeBefore, repository.size(), "Analysis changed repository size.");
        assertEquals(mutationCountBefore, repository.getMutationCalls(),
                "Analysis called a repository mutation.");
    }

    private static void repeatedAnalysisUsesCurrentData() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        ExpenseAnalyticsService analyticsService = analyticsService(repository);

        ExpenseAnalyticsSnapshot first = analyticsService.analyzeMonth(SELECTED_MONTH);
        repository.seed(expense(
                "new", SELECTED_MONTH.atDay(10), "8.00", Category.OTHER));
        ExpenseAnalyticsSnapshot second = analyticsService.analyzeMonth(SELECTED_MONTH);

        assertEquals(0, first.getSelectedMonthSummary().getExpenseCount(),
                "First snapshot should remain empty.");
        assertEquals(1, second.getSelectedMonthSummary().getExpenseCount(),
                "Repeated analysis did not reflect current repository data.");
    }

    private static void expectInvalidTrendCount(int trendMonthCount) {
        expectThrows(
                ValidationException.class,
                () -> analyticsService(new InMemoryExpenseRepository())
                        .analyzeMonth(SELECTED_MONTH, trendMonthCount),
                "Invalid trend count should be rejected.");
    }

    private static ExpenseAnalyticsService analyticsService(
            InMemoryExpenseRepository repository) {
        return new ExpenseAnalyticsService(new ExpenseService(repository));
    }

    private static InMemoryExpenseRepository repositoryWithAdjacentMonths() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "earlier",
                SELECTED_MONTH.minusMonths(1).atDay(10),
                "10.00",
                Category.FOOD));
        repository.seed(expense(
                "selected", SELECTED_MONTH.atDay(10), "20.00", Category.BILLS));
        repository.seed(expense(
                "later",
                SELECTED_MONTH.plusMonths(1).atDay(10),
                "30.00",
                Category.OTHER));
        return repository;
    }

    private static InMemoryExpenseRepository repositoryWithSelectedExpenses() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "first", SELECTED_MONTH.atDay(1), "10.00", Category.FOOD));
        repository.seed(expense(
                "second", SELECTED_MONTH.atDay(10), "10.01", Category.BILLS));
        repository.seed(expense(
                "third", SELECTED_MONTH.atEndOfMonth(), "10.01", Category.FOOD));
        return repository;
    }

    private static InMemoryExpenseRepository changeRepository(
            String previousAmount, String selectedAmount) {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.seed(expense(
                "previous",
                SELECTED_MONTH.minusMonths(1).atDay(10),
                previousAmount,
                Category.OTHER));
        repository.seed(expense(
                "selected",
                SELECTED_MONTH.atDay(10),
                selectedAmount,
                Category.OTHER));
        return repository;
    }

    private static ExpenseAnalyticsSnapshot snapshot(
            List<Expense> expenses, Map<YearMonth, BigDecimal> monthlyTotals) {
        return new ExpenseAnalyticsSnapshot(
                SELECTED_MONTH,
                summary(expenses),
                expenses,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                monthlyTotals);
    }

    private static ExpenseSummary summary(List<Expense> expenses) {
        return new ExpenseService(new InMemoryExpenseRepository())
                .calculateSummary(expenses);
    }

    private static Expense expense(
            String id,
            LocalDate date,
            String amount,
            Category category) {
        return new Expense(
                id,
                "Expense " + id,
                new BigDecimal(amount),
                date,
                category,
                "Notes " + id);
    }

    private static List<String> ids(List<Expense> expenses) {
        return expenses.stream().map(Expense::getId).toList();
    }

    private static void assertIds(
            List<Expense> expenses, List<String> expectedIds, String message) {
        assertEquals(expectedIds, ids(expenses), message);
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(new BigDecimal(expected), actual, message);
        assertEquals(2, actual.scale(), message + " Scale should be two.");
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError("Analytics service test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, TestCase action, String message) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    message + " Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(message + " Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }

    private static final class InMemoryExpenseRepository implements ExpenseRepository {

        private final List<Expense> expenses = new ArrayList<>();
        private int findAllCalls;
        private int mutationCalls;
        private RepositoryException readFailure;

        @Override
        public List<Expense> findAll() {
            findAllCalls++;
            if (readFailure != null) {
                throw readFailure;
            }
            return List.copyOf(expenses);
        }

        @Override
        public Optional<Expense> findById(String id) {
            return expenses.stream()
                    .filter(expense -> expense.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Expense expense) {
            mutationCalls++;
            if (findById(expense.getId()).isPresent()) {
                throw new RepositoryException(
                        "Expense ID already exists: " + expense.getId());
            }
            expenses.add(Objects.requireNonNull(expense, "Expense is required."));
        }

        @Override
        public void update(Expense expense) {
            mutationCalls++;
            Objects.requireNonNull(expense, "Expense is required.");
            for (int index = 0; index < expenses.size(); index++) {
                if (expenses.get(index).getId().equals(expense.getId())) {
                    expenses.set(index, expense);
                    return;
                }
            }
            throw new RepositoryException(
                    "Expense ID does not exist: " + expense.getId());
        }

        @Override
        public boolean deleteById(String id) {
            mutationCalls++;
            return expenses.removeIf(expense -> expense.getId().equals(id));
        }

        void seed(Expense expense) {
            expenses.add(expense);
        }

        int size() {
            return expenses.size();
        }

        int getFindAllCalls() {
            return findAllCalls;
        }

        void resetFindAllCalls() {
            findAllCalls = 0;
        }

        int getMutationCalls() {
            return mutationCalls;
        }

        List<Expense> snapshotWithoutCounting() {
            return List.copyOf(expenses);
        }

        void failReads(RepositoryException failure) {
            readFailure = failure;
        }
    }
}
