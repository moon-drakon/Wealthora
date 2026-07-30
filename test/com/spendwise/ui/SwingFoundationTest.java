package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.ExpenseService;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;

public class SwingFoundationTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2020, 5, 10);
    private static int passedTests;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        runSwingTest("empty table model", SwingFoundationTest::emptyModelHasZeroRows);
        runSwingTest("exact table columns", SwingFoundationTest::tableHasExactColumns);
        runSwingTest("replace row count", SwingFoundationTest::replaceUpdatesRowCount);
        runSwingTest("supplied order", SwingFoundationTest::suppliedOrderIsPreserved);
        runSwingTest("date mapping", SwingFoundationTest::dateColumnMapsCorrectly);
        runSwingTest("description mapping", SwingFoundationTest::descriptionColumnMapsCorrectly);
        runSwingTest("category mapping", SwingFoundationTest::categoryColumnMapsCorrectly);
        runSwingTest("amount mapping", SwingFoundationTest::amountColumnMapsExactly);
        runSwingTest("notes mapping", SwingFoundationTest::notesColumnMapsCorrectly);
        runSwingTest("hidden ID access", SwingFoundationTest::expenseIdRemainsAvailable);
        runSwingTest("non-editable cells", SwingFoundationTest::cellsAreNotEditable);
        runSwingTest("defensive list copy", SwingFoundationTest::suppliedListIsCopied);
        runSwingTest("null list rejection", SwingFoundationTest::nullListIsRejected);
        runSwingTest("null element rejection", SwingFoundationTest::nullElementIsRejected);
        runSwingTest("invalid row rejection", SwingFoundationTest::invalidRowsAreRejected);
        runSwingTest("table update event", SwingFoundationTest::replaceFiresTableUpdate);
        runTest("exact amount parsing", SwingFoundationTest::validAmountParsesExactly);
        runTest("ISO date parsing", SwingFoundationTest::validDateParses);
        runTest("blank amount rejection", SwingFoundationTest::blankAmountIsRejected);
        runTest("malformed amount rejection", SwingFoundationTest::malformedAmountIsRejected);
        runTest("blank date rejection", SwingFoundationTest::blankDateIsRejected);
        runTest("malformed date rejection", SwingFoundationTest::malformedDateIsRejected);
        runTest("decimal parsing precision", SwingFoundationTest::amountParsingAvoidsFloatingPoint);
        runSwingTest("headless component construction",
                SwingFoundationTest::componentsConstructHeadlessly);
        runSwingTest("empty summary display", SwingFoundationTest::emptySummaryDisplaysZeros);

        System.out.println("All " + passedTests + " Swing foundation tests passed.");
    }

    private static void emptyModelHasZeroRows() {
        assertEquals(0, new ExpenseTableModel().getRowCount(),
                "New table model should be empty.");
    }

    private static void tableHasExactColumns() {
        ExpenseTableModel model = new ExpenseTableModel();
        List<String> expectedNames =
                List.of("Date", "Description", "Category", "Amount", "Notes");
        List<Class<?>> expectedClasses = List.of(
                LocalDate.class,
                String.class,
                String.class,
                BigDecimal.class,
                String.class);

        assertEquals(5, model.getColumnCount(), "Unexpected table column count.");
        for (int column = 0; column < expectedNames.size(); column++) {
            assertEquals(expectedNames.get(column), model.getColumnName(column),
                    "Unexpected table column name.");
            assertEquals(expectedClasses.get(column), model.getColumnClass(column),
                    "Unexpected table column class.");
        }
    }

    private static void replaceUpdatesRowCount() {
        ExpenseTableModel model = new ExpenseTableModel();

        model.replaceExpenses(List.of(expense("one"), expense("two")));

        assertEquals(2, model.getRowCount(), "Replacing expenses did not update rows.");
    }

    private static void suppliedOrderIsPreserved() {
        ExpenseTableModel model = new ExpenseTableModel();
        model.replaceExpenses(List.of(expense("second"), expense("first")));

        assertEquals("second", model.getExpenseAt(0).getId(),
                "Table changed the supplied order.");
        assertEquals("first", model.getExpenseAt(1).getId(),
                "Table changed the supplied order.");
    }

    private static void dateColumnMapsCorrectly() {
        ExpenseTableModel model = modelWithOneExpense();

        assertEquals(TEST_DATE, model.getValueAt(0, 0), "Date column is incorrect.");
    }

    private static void descriptionColumnMapsCorrectly() {
        ExpenseTableModel model = modelWithOneExpense();

        assertEquals("Reference book", model.getValueAt(0, 1),
                "Description column is incorrect.");
    }

    private static void categoryColumnMapsCorrectly() {
        ExpenseTableModel model = modelWithOneExpense();

        assertEquals(Category.EDUCATION.getDisplayName(), model.getValueAt(0, 2),
                "Category display name is incorrect.");
    }

    private static void amountColumnMapsExactly() {
        ExpenseTableModel model = modelWithOneExpense();
        Object value = model.getValueAt(0, 3);

        assertEquals(BigDecimal.class, value.getClass(),
                "Amount column should retain BigDecimal.");
        assertEquals(new BigDecimal("45.60"), value,
                "Amount column changed the exact decimal value.");
    }

    private static void notesColumnMapsCorrectly() {
        ExpenseTableModel model = modelWithOneExpense();

        assertEquals("Second edition", model.getValueAt(0, 4),
                "Notes column is incorrect.");
    }

    private static void expenseIdRemainsAvailable() {
        ExpenseTableModel model = modelWithOneExpense();

        assertEquals("expense-001", model.getExpenseAt(0).getId(),
                "Expense ID was not retained for CRUD selection.");
    }

    private static void cellsAreNotEditable() {
        ExpenseTableModel model = modelWithOneExpense();

        for (int column = 0; column < model.getColumnCount(); column++) {
            assertFalse(model.isCellEditable(0, column),
                    "Expense table cells must not be editable.");
        }
    }

    private static void suppliedListIsCopied() {
        ExpenseTableModel model = new ExpenseTableModel();
        List<Expense> supplied = new ArrayList<>();
        supplied.add(expense("one"));
        model.replaceExpenses(supplied);

        supplied.add(expense("two"));

        assertEquals(1, model.getRowCount(),
                "Table model retained the caller's mutable list.");
    }

    private static void nullListIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new ExpenseTableModel().replaceExpenses(null),
                "Null expense list should be rejected.");
    }

    private static void nullElementIsRejected() {
        List<Expense> expenses = Arrays.asList(expense("one"), null);

        expectThrows(
                IllegalArgumentException.class,
                () -> new ExpenseTableModel().replaceExpenses(expenses),
                "Null expense element should be rejected.");
    }

    private static void invalidRowsAreRejected() {
        ExpenseTableModel model = modelWithOneExpense();

        expectThrows(
                IndexOutOfBoundsException.class,
                () -> model.getExpenseAt(-1),
                "Negative row should be rejected.");
        expectThrows(
                IndexOutOfBoundsException.class,
                () -> model.getExpenseAt(1),
                "Row past the end should be rejected.");
    }

    private static void replaceFiresTableUpdate() {
        ExpenseTableModel model = new ExpenseTableModel();
        AtomicInteger eventCount = new AtomicInteger();
        AtomicReference<TableModelEvent> lastEvent = new AtomicReference<>();
        model.addTableModelListener(event -> {
            eventCount.incrementAndGet();
            lastEvent.set(event);
        });

        model.replaceExpenses(List.of(expense("one")));

        assertEquals(1, eventCount.get(), "Replacing data should fire one table event.");
        assertEquals(TableModelEvent.UPDATE, lastEvent.get().getType(),
                "Replacing data should fire an update event.");
    }

    private static void validAmountParsesExactly() {
        assertEquals(
                new BigDecimal("123.45"),
                ExpenseFormDialog.parseAmount("  123.45  "),
                "Valid amount text was not parsed exactly.");
    }

    private static void validDateParses() {
        assertEquals(
                LocalDate.of(2024, 2, 29),
                ExpenseFormDialog.parseDate("  2024-02-29  "),
                "Valid ISO date was not parsed correctly.");
    }

    private static void blankAmountIsRejected() {
        expectThrows(
                NumberFormatException.class,
                () -> ExpenseFormDialog.parseAmount("   "),
                "Blank amount should be rejected.");
    }

    private static void malformedAmountIsRejected() {
        expectThrows(
                NumberFormatException.class,
                () -> ExpenseFormDialog.parseAmount("12,50"),
                "Malformed amount should be rejected.");
    }

    private static void blankDateIsRejected() {
        expectThrows(
                DateTimeParseException.class,
                () -> ExpenseFormDialog.parseDate(" "),
                "Blank date should be rejected.");
    }

    private static void malformedDateIsRejected() {
        expectThrows(
                DateTimeParseException.class,
                () -> ExpenseFormDialog.parseDate("10/05/2020"),
                "Malformed date should be rejected.");
    }

    private static void amountParsingAvoidsFloatingPoint() {
        BigDecimal parsed =
                ExpenseFormDialog.parseAmount("12345678901234567890.01");

        assertEquals(
                new BigDecimal("12345678901234567890.01"),
                parsed,
                "Amount parsing lost decimal precision.");
    }

    private static void componentsConstructHeadlessly() {
        ExpenseTableModel model = new ExpenseTableModel();
        JTable table = new JTable(model);
        ExpensePanel panel = new ExpensePanel(
                new ExpenseService(new InMemoryExpenseRepository()));

        assertEquals(model, table.getModel(), "JTable did not retain its model.");
        assertTrue(panel.getComponentCount() > 0,
                "Expense panel should contain non-window Swing components.");
    }

    private static void emptySummaryDisplaysZeros() {
        ExpensePanel panel = new ExpensePanel(
                new ExpenseService(new InMemoryExpenseRepository()));

        assertEquals("0", panel.getDisplayedExpenseCountText(),
                "Empty expense count should display zero.");
        assertEquals("0.00", panel.getDisplayedTotalAmountText(),
                "Empty total should display 0.00.");
        assertEquals("0.00", panel.getDisplayedAverageAmountText(),
                "Empty average should display 0.00.");
    }

    private static ExpenseTableModel modelWithOneExpense() {
        ExpenseTableModel model = new ExpenseTableModel();
        model.replaceExpenses(List.of(new Expense(
                "expense-001",
                "Reference book",
                new BigDecimal("45.60"),
                TEST_DATE,
                Category.EDUCATION,
                "Second edition")));
        return model;
    }

    private static Expense expense(String id) {
        return new Expense(
                id,
                "Test expense",
                new BigDecimal("10.00"),
                TEST_DATE,
                Category.OTHER,
                "Test note");
    }

    private static void runSwingTest(String name, TestCase test) {
        runTest(name, () -> runOnEventDispatchThread(test));
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError("Swing foundation test failed: " + name, exception);
        }
    }

    private static void runOnEventDispatchThread(TestCase test)
            throws InvocationTargetException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                test.run();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return;
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
    }

    private static <T extends Throwable> void expectThrows(
            Class<T> expectedType, TestCase action, String message) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return;
            }
            throw new AssertionError(
                    message + " Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(
                message + " Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
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

        @Override
        public List<Expense> findAll() {
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
            if (findById(expense.getId()).isPresent()) {
                throw new RepositoryException(
                        "Expense ID already exists: " + expense.getId());
            }
            expenses.add(Objects.requireNonNull(expense, "Expense is required."));
        }

        @Override
        public void update(Expense expense) {
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
            return expenses.removeIf(expense -> expense.getId().equals(id));
        }
    }
}
