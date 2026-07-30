package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class ExpenseTest {

    private static final LocalDate VALID_DATE = LocalDate.now().minusDays(1);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("category display names", ExpenseTest::categoryDisplayNames);
        runTest("automatic UUID creation", ExpenseTest::automaticUuidCreation);
        runTest("existing ID preservation", ExpenseTest::existingIdPreservation);
        runTest("input trimming", ExpenseTest::inputTrimming);
        runTest("null notes normalization", ExpenseTest::nullNotesBecomeEmpty);
        runTest("amount normalization", ExpenseTest::validAmountIsNormalized);
        runTest("successful details update", ExpenseTest::successfulUpdateDetails);
        runTest("identity equality", ExpenseTest::matchingIdsAreEqual);
        runTest("null ID rejection", ExpenseTest::nullIdIsRejected);
        runTest("blank ID rejection", ExpenseTest::blankIdIsRejected);
        runTest("ID length limit", ExpenseTest::idLengthLimit);
        runTest("blank description rejection", ExpenseTest::blankDescriptionIsRejected);
        runTest("description length limit", ExpenseTest::descriptionLengthLimit);
        runTest("null amount rejection", ExpenseTest::nullAmountIsRejected);
        runTest("zero amount rejection", ExpenseTest::zeroAmountIsRejected);
        runTest("negative amount rejection", ExpenseTest::negativeAmountIsRejected);
        runTest("maximum amount limit", ExpenseTest::excessiveAmountIsRejected);
        runTest("decimal-place limit", ExpenseTest::extraDecimalPlacesAreRejected);
        runTest("null date rejection", ExpenseTest::nullDateIsRejected);
        runTest("future date rejection", ExpenseTest::futureDateIsRejected);
        runTest("null category rejection", ExpenseTest::nullCategoryIsRejected);
        runTest("notes length limit", ExpenseTest::notesLengthLimit);
        runTest("failed update is atomic", ExpenseTest::failedUpdateLeavesExpenseUnchanged);

        System.out.println("All " + passedTests + " core model tests passed.");
    }

    private static void categoryDisplayNames() {
        Category[] categories = Category.values();
        String[] expectedNames = {
            "Food",
            "Transport",
            "Shopping",
            "Bills",
            "Health",
            "Education",
            "Entertainment",
            "Other"
        };

        assertEquals(expectedNames.length, categories.length, "Unexpected category count.");
        for (int index = 0; index < categories.length; index++) {
            assertEquals(
                    expectedNames[index],
                    categories[index].getDisplayName(),
                    "Incorrect category display name.");
            assertEquals(
                    expectedNames[index],
                    categories[index].toString(),
                    "Category toString should return its display name.");
        }
    }

    private static void automaticUuidCreation() {
        Expense expense = createValidExpense();
        UUID parsedId = UUID.fromString(expense.getId());

        assertEquals(parsedId.toString(), expense.getId(), "Generated ID should be a UUID.");
    }

    private static void existingIdPreservation() {
        Expense expense = createExpenseWithId("expense-001");

        assertEquals("expense-001", expense.getId(), "Existing ID should be preserved.");
    }

    private static void inputTrimming() {
        Expense expense = new Expense(
                "  expense-001  ",
                "  Lunch  ",
                new BigDecimal("12.50"),
                VALID_DATE,
                Category.FOOD,
                "  Team meal  ");

        assertEquals("expense-001", expense.getId(), "ID should be trimmed.");
        assertEquals("Lunch", expense.getDescription(), "Description should be trimmed.");
        assertEquals("Team meal", expense.getNotes(), "Notes should be trimmed.");
    }

    private static void nullNotesBecomeEmpty() {
        Expense expense = new Expense(
                "Lunch",
                new BigDecimal("12.50"),
                VALID_DATE,
                Category.FOOD,
                null);

        assertEquals("", expense.getNotes(), "Null notes should become an empty string.");
    }

    private static void validAmountIsNormalized() {
        Expense expense = new Expense(
                "Lunch",
                new BigDecimal("12.3400"),
                VALID_DATE,
                Category.FOOD,
                "");

        assertEquals(new BigDecimal("12.34"), expense.getAmount(), "Amount value was not preserved.");
        assertEquals(2, expense.getAmount().scale(), "Amount should use two decimal places.");
    }

    private static void successfulUpdateDetails() {
        Expense expense = createExpenseWithId("expense-001");

        expense.updateDetails(
                "  Bus fare  ",
                new BigDecimal("45"),
                VALID_DATE.minusDays(1),
                Category.TRANSPORT,
                "  Return trip  ");

        assertEquals("expense-001", expense.getId(), "Updating details must not change the ID.");
        assertEquals("Bus fare", expense.getDescription(), "Description was not updated.");
        assertEquals(new BigDecimal("45.00"), expense.getAmount(), "Amount was not updated.");
        assertEquals(VALID_DATE.minusDays(1), expense.getDate(), "Date was not updated.");
        assertEquals(Category.TRANSPORT, expense.getCategory(), "Category was not updated.");
        assertEquals("Return trip", expense.getNotes(), "Notes were not updated.");
    }

    private static void matchingIdsAreEqual() {
        Expense first = createExpenseWithId("shared-id");
        Expense second = new Expense(
                "shared-id",
                "Book",
                new BigDecimal("30.00"),
                VALID_DATE,
                Category.EDUCATION,
                "Reference book");

        assertTrue(first.equals(second), "Expenses with the same ID should be equal.");
        assertTrue(second.equals(first), "Expense equality should be symmetric.");
        assertEquals(first.hashCode(), second.hashCode(), "Matching IDs need matching hash codes.");
    }

    private static void nullIdIsRejected() {
        expectValidation(
                "Expense ID is required.",
                () -> new Expense(
                        null,
                        "Lunch",
                        new BigDecimal("12.50"),
                        VALID_DATE,
                        Category.FOOD,
                        ""));
    }

    private static void blankIdIsRejected() {
        expectValidation(
                "Expense ID is required.",
                () -> createExpenseWithId("   "));
    }

    private static void idLengthLimit() {
        expectValidation(
                "Expense ID must not exceed 100 characters.",
                () -> createExpenseWithId("i".repeat(101)));
    }

    private static void blankDescriptionIsRejected() {
        expectValidation(
                "Description is required.",
                () -> new Expense(
                        "   ",
                        new BigDecimal("12.50"),
                        VALID_DATE,
                        Category.FOOD,
                        ""));
    }

    private static void descriptionLengthLimit() {
        expectValidation(
                "Description must not exceed 100 characters.",
                () -> new Expense(
                        "d".repeat(101),
                        new BigDecimal("12.50"),
                        VALID_DATE,
                        Category.FOOD,
                        ""));
    }

    private static void nullAmountIsRejected() {
        expectValidation(
                "Amount is required.",
                () -> new Expense("Lunch", null, VALID_DATE, Category.FOOD, ""));
    }

    private static void zeroAmountIsRejected() {
        expectValidation(
                "Amount must be greater than zero.",
                () -> new Expense(
                        "Lunch",
                        BigDecimal.ZERO,
                        VALID_DATE,
                        Category.FOOD,
                        ""));
    }

    private static void negativeAmountIsRejected() {
        expectValidation(
                "Amount must be greater than zero.",
                () -> new Expense(
                        "Lunch",
                        new BigDecimal("-0.01"),
                        VALID_DATE,
                        Category.FOOD,
                        ""));
    }

    private static void excessiveAmountIsRejected() {
        expectValidation(
                "Amount must not exceed 999999999.99.",
                () -> new Expense(
                        "Equipment",
                        new BigDecimal("1000000000.00"),
                        VALID_DATE,
                        Category.SHOPPING,
                        ""));
    }

    private static void extraDecimalPlacesAreRejected() {
        expectValidation(
                "Amount must have no more than two decimal places.",
                () -> new Expense(
                        "Lunch",
                        new BigDecimal("12.345"),
                        VALID_DATE,
                        Category.FOOD,
                        ""));
    }

    private static void nullDateIsRejected() {
        expectValidation(
                "Date is required.",
                () -> new Expense(
                        "Lunch",
                        new BigDecimal("12.50"),
                        null,
                        Category.FOOD,
                        ""));
    }

    private static void futureDateIsRejected() {
        expectValidation(
                "Date cannot be in the future.",
                () -> new Expense(
                        "Lunch",
                        new BigDecimal("12.50"),
                        LocalDate.now().plusYears(1),
                        Category.FOOD,
                        ""));
    }

    private static void nullCategoryIsRejected() {
        expectValidation(
                "Category is required.",
                () -> new Expense(
                        "Lunch",
                        new BigDecimal("12.50"),
                        VALID_DATE,
                        null,
                        ""));
    }

    private static void notesLengthLimit() {
        expectValidation(
                "Notes must not exceed 300 characters.",
                () -> new Expense(
                        "Lunch",
                        new BigDecimal("12.50"),
                        VALID_DATE,
                        Category.FOOD,
                        "n".repeat(301)));
    }

    private static void failedUpdateLeavesExpenseUnchanged() {
        Expense expense = new Expense(
                "expense-001",
                "Lunch",
                new BigDecimal("12.50"),
                VALID_DATE,
                Category.FOOD,
                "Original note");
        String originalId = expense.getId();
        String originalDescription = expense.getDescription();
        BigDecimal originalAmount = expense.getAmount();
        LocalDate originalDate = expense.getDate();
        Category originalCategory = expense.getCategory();
        String originalNotes = expense.getNotes();

        expectValidation(
                "Notes must not exceed 300 characters.",
                () -> expense.updateDetails(
                        "Changed description",
                        new BigDecimal("99.99"),
                        VALID_DATE.minusDays(2),
                        Category.SHOPPING,
                        "n".repeat(301)));

        assertEquals(originalId, expense.getId(), "Failed update changed the ID.");
        assertEquals(
                originalDescription,
                expense.getDescription(),
                "Failed update changed the description.");
        assertEquals(originalAmount, expense.getAmount(), "Failed update changed the amount.");
        assertEquals(originalDate, expense.getDate(), "Failed update changed the date.");
        assertEquals(originalCategory, expense.getCategory(), "Failed update changed the category.");
        assertEquals(originalNotes, expense.getNotes(), "Failed update changed the notes.");
    }

    private static Expense createValidExpense() {
        return new Expense(
                "Lunch",
                new BigDecimal("12.50"),
                VALID_DATE,
                Category.FOOD,
                "Team meal");
    }

    private static Expense createExpenseWithId(String id) {
        return new Expense(
                id,
                "Lunch",
                new BigDecimal("12.50"),
                VALID_DATE,
                Category.FOOD,
                "Team meal");
    }

    private static void runTest(String name, Runnable test) {
        try {
            test.run();
            passedTests++;
        } catch (AssertionError | RuntimeException exception) {
            throw new AssertionError("Test failed: " + name, exception);
        }
    }

    private static void expectValidation(String expectedMessage, Runnable action) {
        try {
            action.run();
        } catch (ValidationException exception) {
            assertEquals(
                    expectedMessage,
                    exception.getMessage(),
                    "Unexpected validation message.");
            return;
        }
        throw new AssertionError("Expected ValidationException: " + expectedMessage);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }
}
