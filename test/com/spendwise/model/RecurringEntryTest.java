package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class RecurringEntryTest {

    private static int passed;

    private RecurringEntryTest() {
    }

    public static void main(String[] args) {
        test("daily recurrence", () -> assertEquals(
                LocalDate.of(2024, 1, 4),
                RecurrenceFrequency.DAILY.nextDate(
                        LocalDate.of(2024, 1, 1), 3,
                        LocalDate.of(2024, 1, 1))));
        test("weekly recurrence", () -> assertEquals(
                LocalDate.of(2024, 1, 15),
                RecurrenceFrequency.WEEKLY.nextDate(
                        LocalDate.of(2024, 1, 1), 2,
                        LocalDate.of(2024, 1, 1))));
        test("monthly month-end recurrence", RecurringEntryTest::monthEnd);
        test("yearly leap recurrence", RecurringEntryTest::leapYear);
        test("expense definition fields", RecurringEntryTest::expenseFields);
        test("income definition fields", RecurringEntryTest::incomeFields);
        test("transfer definition fields", RecurringEntryTest::transferFields);
        test("positive interval required", () -> expect(
                ValidationException.class,
                () -> definition(RecurringEntryType.EXPENSE, 0, true)));
        test("end date follows start", () -> expect(
                ValidationException.class,
                RecurringEntryTest::endBeforeStart));
        test("active next due respects end", () -> expect(
                ValidationException.class,
                RecurringEntryTest::activeAfterEnd));
        test("inactive next due may follow end", RecurringEntryTest::inactiveAfterEnd);
        test("expense category required", () -> expect(
                NullPointerException.class,
                RecurringEntryTest::missingExpenseCategory));
        test("income rejects category", () -> expect(
                ValidationException.class,
                RecurringEntryTest::incomeWithCategory));
        test("transfer destination required", () -> expect(
                NullPointerException.class,
                RecurringEntryTest::missingTransferDestination));
        test("transfer accounts differ", () -> expect(
                ValidationException.class,
                RecurringEntryTest::sameTransferAccount));
        test("inactive definition is not due", RecurringEntryTest::inactiveNotDue);
        test("next due advancement is immutable", RecurringEntryTest::immutableAdvance);
        System.out.println("All " + passed + " recurring model tests passed.");
    }

    private static void monthEnd() {
        LocalDate anchor = LocalDate.of(2024, 1, 31);
        LocalDate february = RecurrenceFrequency.MONTHLY.nextDate(
                anchor, 1, anchor);
        LocalDate march = RecurrenceFrequency.MONTHLY.nextDate(
                february, 1, anchor);
        assertEquals(LocalDate.of(2024, 2, 29), february);
        assertEquals(LocalDate.of(2024, 3, 31), march);
    }

    private static void leapYear() {
        LocalDate anchor = LocalDate.of(2024, 2, 29);
        LocalDate current = anchor;
        for (int year = 2025; year <= 2028; year++) {
            current = RecurrenceFrequency.YEARLY.nextDate(current, 1, anchor);
            assertEquals(
                    LocalDate.of(year, 2, year == 2028 ? 29 : 28), current);
        }
    }

    private static void expenseFields() {
        RecurringEntry entry = definition(
                RecurringEntryType.EXPENSE, 2, true);
        assertEquals(new BigDecimal("25.00"), entry.getAmount());
        assertEquals(Category.FOOD, entry.getCategory().orElseThrow());
        assertEquals(2, entry.getInterval());
        assertTrue(entry.getIdentifier().startsWith("RECURRING_"));
    }

    private static void incomeFields() {
        RecurringEntry entry = definition(
                RecurringEntryType.INCOME, 1, true);
        assertTrue(entry.getCategory().isEmpty());
        assertTrue(entry.getDestinationAccount().isEmpty());
    }

    private static void transferFields() {
        RecurringEntry entry = definition(
                RecurringEntryType.TRANSFER, 1, true);
        assertEquals(BANK, entry.getDestinationAccount().orElseThrow());
    }

    private static void endBeforeStart() {
        new RecurringEntry(
                "RECURRING_BAD_END",
                RecurringEntryType.INCOME,
                new BigDecimal("1.00"),
                "Pay",
                null,
                Account.DEFAULT,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 2, 1),
                true);
    }

    private static void activeAfterEnd() {
        new RecurringEntry(
                "RECURRING_BAD_DUE",
                RecurringEntryType.INCOME,
                new BigDecimal("1.00"),
                "Pay",
                null,
                Account.DEFAULT,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 1),
                true);
    }

    private static void inactiveAfterEnd() {
        RecurringEntry entry = new RecurringEntry(
                "RECURRING_ENDED",
                RecurringEntryType.INCOME,
                new BigDecimal("1.00"),
                "Pay",
                null,
                Account.DEFAULT,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 1),
                false);
        assertFalse(entry.isActive());
    }

    private static void incomeWithCategory() {
        new RecurringEntry(
                "RECURRING_BAD_INCOME",
                RecurringEntryType.INCOME,
                new BigDecimal("1.00"),
                "Pay",
                Category.FOOD,
                Account.DEFAULT,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 1),
                null,
                LocalDate.of(2024, 1, 1),
                true);
    }

    private static void missingExpenseCategory() {
        new RecurringEntry(
                "RECURRING_NO_CATEGORY",
                RecurringEntryType.EXPENSE,
                new BigDecimal("1.00"),
                "Food",
                null,
                Account.DEFAULT,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 1),
                null,
                LocalDate.of(2024, 1, 1),
                true);
    }

    private static void missingTransferDestination() {
        new RecurringEntry(
                "RECURRING_NO_DESTINATION",
                RecurringEntryType.TRANSFER,
                new BigDecimal("1.00"),
                "Move",
                null,
                Account.DEFAULT,
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 1),
                null,
                LocalDate.of(2024, 1, 1),
                true);
    }

    private static void sameTransferAccount() {
        new RecurringEntry(
                "RECURRING_BAD_TRANSFER",
                RecurringEntryType.TRANSFER,
                new BigDecimal("1.00"),
                "Move",
                null,
                Account.DEFAULT,
                Account.DEFAULT,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 1),
                null,
                LocalDate.of(2024, 1, 1),
                true);
    }

    private static void inactiveNotDue() {
        RecurringEntry entry = definition(
                RecurringEntryType.EXPENSE, 1, false);
        assertFalse(entry.isDueOnOrBefore(LocalDate.of(2024, 12, 31)));
    }

    private static void immutableAdvance() {
        RecurringEntry original = definition(
                RecurringEntryType.EXPENSE, 1, true);
        LocalDate following = original.calculateFollowingDueDate();
        RecurringEntry advanced = original.withNextDueDate(following, true);
        assertEquals(LocalDate.of(2024, 1, 31), original.getNextDueDate());
        assertEquals(LocalDate.of(2024, 2, 29), advanced.getNextDueDate());
        assertEquals(original.getIdentifier(), advanced.getIdentifier());
    }

    private static RecurringEntry definition(
            RecurringEntryType type,
            int interval,
            boolean active) {
        return RecurringEntry.create(
                type,
                new BigDecimal("25"),
                "Monthly item",
                type == RecurringEntryType.EXPENSE ? Category.FOOD : null,
                Account.DEFAULT,
                type == RecurringEntryType.TRANSFER ? BANK : null,
                RecurrenceFrequency.MONTHLY,
                interval,
                LocalDate.of(2024, 1, 31),
                null,
                active);
    }

    private static final Account BANK = Account.createCustom(
            "ACCOUNT_RECURRING_BANK",
            "Recurring Bank",
            AccountType.BANK,
            new BigDecimal("0.00"),
            false);

    private static void test(String name, Runnable action) {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
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

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean value) {
        assertTrue(!value);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }
}
