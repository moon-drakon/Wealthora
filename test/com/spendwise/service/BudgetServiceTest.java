package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BudgetServiceTest {

    private static final YearMonth MONTH = YearMonth.of(2024, 6);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("null repository", BudgetServiceTest::nullRepositoryIsRejected);
        runTest("null get month", BudgetServiceTest::nullGetMonthIsRejected);
        runTest("null save budget", BudgetServiceTest::nullSaveIsRejected);
        runTest("null clear month", BudgetServiceTest::nullClearIsRejected);
        runTest("null analytics", BudgetServiceTest::nullAnalyticsIsRejected);
        runTest("missing budget", BudgetServiceTest::missingMonthReturnsEmpty);
        runTest("single get call", BudgetServiceTest::getCallsRepositoryOnce);
        runTest("stored budget identity", BudgetServiceTest::storedBudgetIsReturned);
        runTest("empty save rejection", BudgetServiceTest::emptyPlanCannotBeSaved);
        runTest("overall save", BudgetServiceTest::overallCanBeSaved);
        runTest("category save", BudgetServiceTest::categoriesCanBeSaved);
        runTest("single save delegation", BudgetServiceTest::saveDelegatesOnce);
        runTest("single clear delegation", BudgetServiceTest::clearDelegatesOnce);
        runTest("clear result", BudgetServiceTest::clearResultIsPreserved);
        runTest("single evaluation read", BudgetServiceTest::evaluationReadsOnce);
        runTest("no expense reload", BudgetServiceTest::evaluationDoesNotReloadExpenses);
        runTest("analytics total", BudgetServiceTest::evaluationUsesAnalyticsTotal);
        runTest("analytics categories", BudgetServiceTest::evaluationUsesCategoryTotals);
        runTest("all categories", BudgetServiceTest::everyCategoryIsIncluded);
        runTest("not-set level", BudgetServiceTest::noLimitIsNotSet);
        runTest("not-set remaining", BudgetServiceTest::noLimitHasNoRemaining);
        runTest("not-set percentage", BudgetServiceTest::noLimitHasNoPercentage);
        runTest("below 80 percent", BudgetServiceTest::belowEightyIsWithin);
        runTest("exactly 80 percent", BudgetServiceTest::exactlyEightyIsNear);
        runTest("between thresholds", BudgetServiceTest::betweenThresholdsIsNear);
        runTest("exactly 100 percent", BudgetServiceTest::exactlyHundredIsReached);
        runTest("above 100 percent", BudgetServiceTest::aboveHundredIsOver);
        runTest("zero spending", BudgetServiceTest::zeroSpendingIsWithin);
        runTest("exact remaining", BudgetServiceTest::remainingIsExact);
        runTest("negative remaining", BudgetServiceTest::overspendingIsNegative);
        runTest("percentage rounding", BudgetServiceTest::percentageUsesHalfUp);
        runTest("percentage above 100", BudgetServiceTest::percentageCanExceedHundred);
        runTest("independent limits", BudgetServiceTest::limitsAreIndependent);
        runTest("highest alert", BudgetServiceTest::highestAlertIsCorrect);
        runTest("immutable results", BudgetServiceTest::resultsAreImmutable);
        runTest("snapshot mutation safety", BudgetServiceTest::snapshotIsNotMutated);
        runTest("repository failure", BudgetServiceTest::repositoryFailurePropagates);
        runTest("current repository data", BudgetServiceTest::repeatedCallsUseCurrentData);
        runTest("read-only evaluation", BudgetServiceTest::evaluationDoesNotWrite);
        runTest("expense mutation safety", BudgetServiceTest::expensesAreNotMutated);

        System.out.println("All " + passedTests + " budget service tests passed.");
    }

    private static void nullRepositoryIsRejected() {
        expectThrows(NullPointerException.class, () -> new BudgetService(null));
    }

    private static void nullGetMonthIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> service(new InMemoryBudgetRepository()).getBudget(null));
    }

    private static void nullSaveIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> service(new InMemoryBudgetRepository()).saveBudget(null));
    }

    private static void nullClearIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> service(new InMemoryBudgetRepository()).clearBudget(null));
    }

    private static void nullAnalyticsIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> service(new InMemoryBudgetRepository()).evaluate(null));
    }

    private static void missingMonthReturnsEmpty() {
        MonthlyBudget budget =
                service(new InMemoryBudgetRepository()).getBudget(MONTH);
        assertEquals(MONTH, budget.getMonth(), "Empty budget month is wrong.");
        assertFalse(budget.hasAnyLimit(), "Missing month should return an empty plan.");
    }

    private static void getCallsRepositoryOnce() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        service(repository).getBudget(MONTH);
        assertEquals(1, repository.findCalls, "getBudget should read once.");
    }

    private static void storedBudgetIsReturned() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        MonthlyBudget expected = overall("100.00");
        repository.seed(expected);
        assertSame(expected, service(repository).getBudget(MONTH),
                "Stored budget object was not returned.");
    }

    private static void emptyPlanCannotBeSaved() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        expectThrows(
                ValidationException.class,
                () -> service(repository).saveBudget(MonthlyBudget.empty(MONTH)));
        assertEquals(0, repository.saveCalls, "Empty plan reached the repository.");
    }

    private static void overallCanBeSaved() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        service(repository).saveBudget(overall("100.00"));
        assertMoney(
                "100.00",
                repository.stored.getOverallLimit().orElseThrow(),
                "Overall limit was not saved.");
    }

    private static void categoriesCanBeSaved() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        MonthlyBudget budget = category(Category.FOOD, "25.00");
        service(repository).saveBudget(budget);
        assertMoney(
                "25.00",
                repository.stored.getCategoryLimit(Category.FOOD).orElseThrow(),
                "Category limit was not saved.");
    }

    private static void saveDelegatesOnce() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        MonthlyBudget budget = overall("100.00");
        service(repository).saveBudget(budget);
        assertEquals(1, repository.saveCalls, "Save was not delegated once.");
        assertSame(budget, repository.stored, "Save changed the budget object.");
    }

    private static void clearDelegatesOnce() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        service(repository).clearBudget(MONTH);
        assertEquals(1, repository.deleteCalls, "Clear was not delegated once.");
    }

    private static void clearResultIsPreserved() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        repository.seed(overall("10.00"));
        assertTrue(service(repository).clearBudget(MONTH),
                "Existing clear should return true.");
        assertFalse(service(repository).clearBudget(MONTH),
                "Missing clear should return false.");
    }

    private static void evaluationReadsOnce() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        repository.seed(overall("100.00"));
        service(repository).evaluate(snapshot("50.00", Map.of(), List.of()));
        assertEquals(1, repository.findCalls, "Evaluation should read one budget.");
    }

    private static void evaluationDoesNotReloadExpenses() {
        Expense expense = expense("one", "10.00");
        ExpenseAnalyticsSnapshot snapshot =
                snapshot("10.00", Map.of(Category.FOOD, "10.00"), List.of(expense));
        service(new InMemoryBudgetRepository()).evaluate(snapshot);
        assertSame(expense, snapshot.getSelectedMonthExpenses().get(0),
                "Evaluation replaced analytics expenses.");
    }

    private static void evaluationUsesAnalyticsTotal() {
        BudgetStatusSnapshot status =
                evaluate("42.35", overall("100.00"), Map.of());
        assertMoney("42.35", status.getOverallUsage().getSpent(),
                "Analytics total was not used.");
    }

    private static void evaluationUsesCategoryTotals() {
        BudgetStatusSnapshot status = evaluate(
                "30.00",
                category(Category.EDUCATION, "50.00"),
                Map.of(Category.EDUCATION, "12.34"));
        assertMoney(
                "12.34",
                status.getUsageForCategory(Category.EDUCATION).getSpent(),
                "Analytics category total was not used.");
    }

    private static void everyCategoryIsIncluded() {
        BudgetStatusSnapshot status =
                evaluate("0.00", MonthlyBudget.empty(MONTH), Map.of());
        assertEquals(Category.values().length, status.getCategoryUsage().size(),
                "Not every category was included.");
    }

    private static void noLimitIsNotSet() {
        assertEquals(
                BudgetAlertLevel.NOT_SET,
                evaluate("50.00", MonthlyBudget.empty(MONTH), Map.of())
                        .getOverallUsage()
                        .getAlertLevel(),
                "Missing limit should be NOT_SET.");
    }

    private static void noLimitHasNoRemaining() {
        assertTrue(
                evaluate("50.00", MonthlyBudget.empty(MONTH), Map.of())
                        .getOverallUsage()
                        .getRemaining()
                        .isEmpty(),
                "Missing limit should have no remaining amount.");
    }

    private static void noLimitHasNoPercentage() {
        assertTrue(
                evaluate("50.00", MonthlyBudget.empty(MONTH), Map.of())
                        .getOverallUsage()
                        .getUsagePercentage()
                        .isEmpty(),
                "Missing limit should have no percentage.");
    }

    private static void belowEightyIsWithin() {
        assertAlert("79.99", "100.00", BudgetAlertLevel.WITHIN_LIMIT);
    }

    private static void exactlyEightyIsNear() {
        assertAlert("80.00", "100.00", BudgetAlertLevel.NEAR_LIMIT);
    }

    private static void betweenThresholdsIsNear() {
        assertAlert("99.99", "100.00", BudgetAlertLevel.NEAR_LIMIT);
    }

    private static void exactlyHundredIsReached() {
        assertAlert("100.00", "100.00", BudgetAlertLevel.LIMIT_REACHED);
    }

    private static void aboveHundredIsOver() {
        assertAlert("100.01", "100.00", BudgetAlertLevel.OVER_LIMIT);
    }

    private static void zeroSpendingIsWithin() {
        assertAlert("0.00", "100.00", BudgetAlertLevel.WITHIN_LIMIT);
    }

    private static void remainingIsExact() {
        BudgetUsage usage = evaluate("31.25", overall("100.00"), Map.of())
                .getOverallUsage();
        assertMoney("68.75", usage.getRemaining().orElseThrow(),
                "Remaining amount is incorrect.");
    }

    private static void overspendingIsNegative() {
        BudgetUsage usage = evaluate("125.50", overall("100.00"), Map.of())
                .getOverallUsage();
        assertMoney("-25.50", usage.getRemaining().orElseThrow(),
                "Overspending remaining should be negative.");
    }

    private static void percentageUsesHalfUp() {
        BudgetUsage usage = evaluate("1.00", overall("3.00"), Map.of())
                .getOverallUsage();
        assertEquals(
                new BigDecimal("33.33"),
                usage.getUsagePercentage().orElseThrow(),
                "Percentage should use two-decimal HALF_UP.");
    }

    private static void percentageCanExceedHundred() {
        BudgetUsage usage = evaluate("10.00", overall("3.00"), Map.of())
                .getOverallUsage();
        assertEquals(
                new BigDecimal("333.33"),
                usage.getUsagePercentage().orElseThrow(),
                "Percentage should be able to exceed 100.00.");
    }

    private static void limitsAreIndependent() {
        MonthlyBudget budget = new MonthlyBudget(
                MONTH,
                Optional.of(new BigDecimal("100.00")),
                Map.of(Category.FOOD, new BigDecimal("5.00")));
        BudgetStatusSnapshot status = evaluate(
                "50.00", budget, Map.of(Category.FOOD, "6.00"));
        assertEquals(BudgetAlertLevel.WITHIN_LIMIT,
                status.getOverallUsage().getAlertLevel(),
                "Overall limit was not evaluated independently.");
        assertEquals(BudgetAlertLevel.OVER_LIMIT,
                status.getUsageForCategory(Category.FOOD).getAlertLevel(),
                "Category limit was not evaluated independently.");
    }

    private static void highestAlertIsCorrect() {
        MonthlyBudget budget = new MonthlyBudget(
                MONTH,
                Optional.of(new BigDecimal("100.00")),
                Map.of(
                        Category.FOOD, new BigDecimal("5.00"),
                        Category.BILLS, new BigDecimal("20.00")));
        BudgetStatusSnapshot status = evaluate(
                "50.00",
                budget,
                Map.of(Category.FOOD, "6.00", Category.BILLS, "20.00"));
        assertEquals(BudgetAlertLevel.OVER_LIMIT,
                status.getHighestActiveAlertLevel(),
                "Highest alert severity is incorrect.");
    }

    private static void resultsAreImmutable() {
        BudgetStatusSnapshot status =
                evaluate("0.00", MonthlyBudget.empty(MONTH), Map.of());
        expectThrows(
                UnsupportedOperationException.class,
                () -> status.getCategoryUsage().put(
                        Category.FOOD,
                        new BudgetUsage(
                                BigDecimal.ZERO, Optional.empty())));
    }

    private static void snapshotIsNotMutated() {
        ExpenseAnalyticsSnapshot snapshot =
                snapshot("10.00", Map.of(Category.FOOD, "10.00"), List.of());
        BigDecimal totalBefore = snapshot.getSelectedMonthSummary().getTotalAmount();
        Map<Category, BigDecimal> categoriesBefore =
                snapshot.getSelectedMonthSummary().getTotalsByCategory();
        service(new InMemoryBudgetRepository()).evaluate(snapshot);
        assertEquals(totalBefore, snapshot.getSelectedMonthSummary().getTotalAmount(),
                "Evaluation changed analytics total.");
        assertSame(categoriesBefore,
                snapshot.getSelectedMonthSummary().getTotalsByCategory(),
                "Evaluation replaced analytics category totals.");
    }

    private static void repositoryFailurePropagates() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        RepositoryException expected = new RepositoryException("Test budget read failure.");
        repository.readFailure = expected;
        RepositoryException actual = expectThrows(
                RepositoryException.class,
                () -> service(repository).evaluate(
                        snapshot("0.00", Map.of(), List.of())));
        assertSame(expected, actual, "RepositoryException was wrapped or swallowed.");
    }

    private static void repeatedCallsUseCurrentData() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        BudgetService service = service(repository);
        ExpenseAnalyticsSnapshot snapshot = snapshot("50.00", Map.of(), List.of());
        repository.seed(overall("100.00"));
        BudgetAlertLevel first = service.evaluate(snapshot)
                .getOverallUsage().getAlertLevel();
        repository.seed(overall("40.00"));
        BudgetAlertLevel second = service.evaluate(snapshot)
                .getOverallUsage().getAlertLevel();
        assertEquals(BudgetAlertLevel.WITHIN_LIMIT, first, "First state is wrong.");
        assertEquals(BudgetAlertLevel.OVER_LIMIT, second,
                "Second evaluation did not use current repository data.");
    }

    private static void evaluationDoesNotWrite() {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        repository.seed(overall("100.00"));
        int mutationsBefore = repository.saveCalls + repository.deleteCalls;
        service(repository).evaluate(snapshot("50.00", Map.of(), List.of()));
        assertEquals(
                mutationsBefore,
                repository.saveCalls + repository.deleteCalls,
                "Evaluation performed a budget mutation.");
    }

    private static void expensesAreNotMutated() {
        Expense expense = expense("one", "10.00");
        String description = expense.getDescription();
        BigDecimal amount = expense.getAmount();
        LocalDate date = expense.getDate();
        Category category = expense.getCategory();
        String notes = expense.getNotes();
        service(new InMemoryBudgetRepository()).evaluate(
                snapshot(
                        "10.00",
                        Map.of(Category.FOOD, "10.00"),
                        List.of(expense)));
        assertEquals(description, expense.getDescription(), "Description changed.");
        assertEquals(amount, expense.getAmount(), "Amount changed.");
        assertEquals(date, expense.getDate(), "Date changed.");
        assertEquals(category, expense.getCategory(), "Category changed.");
        assertEquals(notes, expense.getNotes(), "Notes changed.");
    }

    private static void assertAlert(
            String spent, String limit, BudgetAlertLevel expected) {
        BudgetUsage usage = evaluate(spent, overall(limit), Map.of())
                .getOverallUsage();
        assertEquals(expected, usage.getAlertLevel(), "Alert level is incorrect.");
    }

    private static BudgetStatusSnapshot evaluate(
            String total,
            MonthlyBudget budget,
            Map<Category, String> categoryTotals) {
        InMemoryBudgetRepository repository = new InMemoryBudgetRepository();
        if (budget.hasAnyLimit()) {
            repository.seed(budget);
        }
        return service(repository).evaluate(
                snapshot(total, categoryTotals, List.of()));
    }

    private static ExpenseAnalyticsSnapshot snapshot(
            String total,
            Map<Category, String> suppliedCategoryTotals,
            List<Expense> expenses) {
        LinkedHashMap<Category, BigDecimal> categoryTotals =
                new LinkedHashMap<>();
        for (Category category : Category.values()) {
            categoryTotals.put(
                    category,
                    new BigDecimal(
                            suppliedCategoryTotals.getOrDefault(category, "0.00")));
        }
        BigDecimal totalAmount = new BigDecimal(total);
        ExpenseSummary summary = new ExpenseSummary(
                expenses.size(),
                totalAmount,
                expenses.isEmpty() ? BigDecimal.ZERO : totalAmount,
                categoryTotals);
        LinkedHashMap<YearMonth, BigDecimal> monthlyTotals =
                new LinkedHashMap<>();
        monthlyTotals.put(MONTH, totalAmount);
        return new ExpenseAnalyticsSnapshot(
                MONTH,
                summary,
                expenses,
                BigDecimal.ZERO,
                totalAmount,
                monthlyTotals);
    }

    private static Expense expense(String id, String amount) {
        return new Expense(
                id,
                "Expense " + id,
                new BigDecimal(amount),
                LocalDate.of(2020, 1, 10),
                Category.FOOD,
                "Notes " + id);
    }

    private static MonthlyBudget overall(String amount) {
        return new MonthlyBudget(
                MONTH, Optional.of(new BigDecimal(amount)), Map.of());
    }

    private static MonthlyBudget category(
            Category category, String amount) {
        return new MonthlyBudget(
                MONTH,
                Optional.empty(),
                Map.of(category, new BigDecimal(amount)));
    }

    private static BudgetService service(InMemoryBudgetRepository repository) {
        return new BudgetService(repository);
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError("Budget service test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, TestCase action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError("Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertMoney(
            String expected, BigDecimal actual, String message) {
        assertEquals(new BigDecimal(expected), actual, message);
        assertEquals(2, actual.scale(), message + " Scale should be two.");
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

    private static void assertEquals(
            Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }

    private static final class InMemoryBudgetRepository
            implements BudgetRepository {

        private MonthlyBudget stored;
        private int findCalls;
        private int saveCalls;
        private int deleteCalls;
        private RepositoryException readFailure;

        @Override
        public Optional<MonthlyBudget> findByMonth(YearMonth month) {
            findCalls++;
            if (readFailure != null) {
                throw readFailure;
            }
            return stored != null && stored.getMonth().equals(month)
                    ? Optional.of(stored)
                    : Optional.empty();
        }

        @Override
        public void save(MonthlyBudget budget) {
            saveCalls++;
            stored = budget;
        }

        @Override
        public boolean delete(YearMonth month) {
            deleteCalls++;
            if (stored == null || !stored.getMonth().equals(month)) {
                return false;
            }
            stored = null;
            return true;
        }

        void seed(MonthlyBudget budget) {
            stored = budget;
        }
    }
}
