package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MonthlyBudgetTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("null month", MonthlyBudgetTest::nullMonthIsRejected);
        runTest("null Optional", MonthlyBudgetTest::nullOptionalIsRejected);
        runTest("null map", MonthlyBudgetTest::nullMapIsRejected);
        runTest("null category", MonthlyBudgetTest::nullCategoryIsRejected);
        runTest("null amount", MonthlyBudgetTest::nullAmountIsRejected);
        runTest("zero overall", MonthlyBudgetTest::zeroOverallIsRejected);
        runTest("negative overall", MonthlyBudgetTest::negativeOverallIsRejected);
        runTest("zero category", MonthlyBudgetTest::zeroCategoryIsRejected);
        runTest("negative category", MonthlyBudgetTest::negativeCategoryIsRejected);
        runTest("meaningful decimals", MonthlyBudgetTest::extraDecimalsAreRejected);
        runTest("trailing zeros", MonthlyBudgetTest::trailingZerosAreNormalized);
        runTest("overall scale", MonthlyBudgetTest::overallUsesTwoDecimals);
        runTest("category scale", MonthlyBudgetTest::categoryUsesTwoDecimals);
        runTest("defensive input copy", MonthlyBudgetTest::inputMapIsCopied);
        runTest("unmodifiable output", MonthlyBudgetTest::returnedMapIsUnmodifiable);
        runTest("category order", MonthlyBudgetTest::categoryOrderIsDeterministic);
        runTest("empty plan", MonthlyBudgetTest::emptyPlanHasNoLimits);
        runTest("has any limit", MonthlyBudgetTest::hasAnyLimitIsCorrect);
        runTest("missing category", MonthlyBudgetTest::missingCategoryIsEmpty);
        runTest("configured category", MonthlyBudgetTest::configuredCategoryIsExact);

        System.out.println("All " + passedTests + " monthly budget model tests passed.");
    }

    private static void nullMonthIsRejected() {
        expectThrows(NullPointerException.class,
                () -> new MonthlyBudget(null, Optional.empty(), Map.of()));
    }

    private static void nullOptionalIsRejected() {
        expectThrows(NullPointerException.class,
                () -> new MonthlyBudget(MONTH, null, Map.of()));
    }

    private static void nullMapIsRejected() {
        expectThrows(NullPointerException.class,
                () -> new MonthlyBudget(MONTH, Optional.empty(), null));
    }

    private static void nullCategoryIsRejected() {
        Map<Category, BigDecimal> limits = new LinkedHashMap<>();
        limits.put(null, new BigDecimal("1.00"));
        expectThrows(NullPointerException.class,
                () -> new MonthlyBudget(MONTH, Optional.empty(), limits));
    }

    private static void nullAmountIsRejected() {
        Map<Category, BigDecimal> limits = new LinkedHashMap<>();
        limits.put(Category.FOOD, null);
        expectThrows(NullPointerException.class,
                () -> new MonthlyBudget(MONTH, Optional.empty(), limits));
    }

    private static void zeroOverallIsRejected() {
        expectValidation(() -> budgetWithOverall("0.00"));
    }

    private static void negativeOverallIsRejected() {
        expectValidation(() -> budgetWithOverall("-1.00"));
    }

    private static void zeroCategoryIsRejected() {
        expectValidation(() -> budgetWithCategory(Category.FOOD, "0.00"));
    }

    private static void negativeCategoryIsRejected() {
        expectValidation(() -> budgetWithCategory(Category.FOOD, "-0.01"));
    }

    private static void extraDecimalsAreRejected() {
        expectValidation(() -> budgetWithOverall("10.001"));
        expectValidation(() -> budgetWithCategory(Category.FOOD, "10.009"));
    }

    private static void trailingZerosAreNormalized() {
        MonthlyBudget budget = budgetWithOverall("100.000");
        assertMoney("100.00", budget.getOverallLimit().orElseThrow(),
                "Safe trailing zeros were not normalized.");
    }

    private static void overallUsesTwoDecimals() {
        assertMoney("25.00", budgetWithOverall("25").getOverallLimit().orElseThrow(),
                "Overall limit scale is incorrect.");
    }

    private static void categoryUsesTwoDecimals() {
        assertMoney(
                "9.50",
                budgetWithCategory(Category.FOOD, "9.5")
                        .getCategoryLimit(Category.FOOD)
                        .orElseThrow(),
                "Category limit scale is incorrect.");
    }

    private static void inputMapIsCopied() {
        LinkedHashMap<Category, BigDecimal> input = new LinkedHashMap<>();
        input.put(Category.FOOD, new BigDecimal("10.00"));
        MonthlyBudget budget =
                new MonthlyBudget(MONTH, Optional.empty(), input);
        input.put(Category.FOOD, new BigDecimal("99.00"));
        assertMoney("10.00", budget.getCategoryLimit(Category.FOOD).orElseThrow(),
                "Budget retained the caller's map.");
    }

    private static void returnedMapIsUnmodifiable() {
        MonthlyBudget budget = budgetWithCategory(Category.FOOD, "10.00");
        expectThrows(
                UnsupportedOperationException.class,
                () -> budget.getCategoryLimits().put(
                        Category.BILLS, new BigDecimal("2.00")));
    }

    private static void categoryOrderIsDeterministic() {
        Map<Category, BigDecimal> input = new LinkedHashMap<>();
        input.put(Category.OTHER, new BigDecimal("8.00"));
        input.put(Category.FOOD, new BigDecimal("1.00"));
        input.put(Category.BILLS, new BigDecimal("4.00"));
        MonthlyBudget budget =
                new MonthlyBudget(MONTH, Optional.empty(), input);
        assertEquals(
                List.of(Category.FOOD, Category.BILLS, Category.OTHER),
                List.copyOf(budget.getCategoryLimits().keySet()),
                "Configured categories are not in enum order.");
    }

    private static void emptyPlanHasNoLimits() {
        MonthlyBudget budget = MonthlyBudget.empty(MONTH);
        assertEquals(MONTH, budget.getMonth(), "Empty plan has the wrong month.");
        assertTrue(budget.getOverallLimit().isEmpty(), "Empty plan has an overall limit.");
        assertTrue(budget.getCategoryLimits().isEmpty(), "Empty plan has category limits.");
    }

    private static void hasAnyLimitIsCorrect() {
        assertFalse(MonthlyBudget.empty(MONTH).hasAnyLimit(),
                "Empty plan should report no limits.");
        assertTrue(budgetWithOverall("10.00").hasAnyLimit(),
                "Overall plan should report a limit.");
        assertTrue(budgetWithCategory(Category.FOOD, "10.00").hasAnyLimit(),
                "Category plan should report a limit.");
    }

    private static void missingCategoryIsEmpty() {
        assertTrue(
                budgetWithCategory(Category.FOOD, "10.00")
                        .getCategoryLimit(Category.BILLS)
                        .isEmpty(),
                "Missing category should return Optional.empty().");
    }

    private static void configuredCategoryIsExact() {
        assertMoney(
                "123.45",
                budgetWithCategory(Category.EDUCATION, "123.45")
                        .getCategoryLimit(Category.EDUCATION)
                        .orElseThrow(),
                "Configured category limit changed.");
    }

    private static MonthlyBudget budgetWithOverall(String amount) {
        return new MonthlyBudget(
                MONTH, Optional.of(new BigDecimal(amount)), Map.of());
    }

    private static MonthlyBudget budgetWithCategory(
            Category category, String amount) {
        return new MonthlyBudget(
                MONTH,
                Optional.empty(),
                Map.of(category, new BigDecimal(amount)));
    }

    private static void expectValidation(TestCase action) {
        expectThrows(ValidationException.class, action);
    }

    private static <T extends Throwable> void expectThrows(
            Class<T> expectedType, TestCase action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return;
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

    private static void assertEquals(
            Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError(
                    "Monthly budget model test failed: " + name, exception);
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }
}
