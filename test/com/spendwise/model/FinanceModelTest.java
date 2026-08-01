package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class FinanceModelTest {

    private static int passed;

    private FinanceModelTest() {
    }

    public static void main(String[] args) {
        test("account type labels", FinanceModelTest::accountTypeLabels);
        test("protected default account", FinanceModelTest::protectedDefault);
        test("custom account normalization", FinanceModelTest::customNormalization);
        test("account rename keeps ID", FinanceModelTest::renameKeepsId);
        test("account archive is immutable", FinanceModelTest::archiveIsImmutable);
        test("protected account operations", FinanceModelTest::protectedOperations);
        test("invalid account values", FinanceModelTest::invalidAccountValues);
        test("income generated ID", FinanceModelTest::incomeGeneratedId);
        test("income normalization", FinanceModelTest::incomeNormalization);
        test("income validation", FinanceModelTest::incomeValidation);
        test("transfer generated ID", FinanceModelTest::transferGeneratedId);
        test("transfer normalization", FinanceModelTest::transferNormalization);
        test("self transfer rejection", FinanceModelTest::selfTransferRejection);
        test("transfer validation", FinanceModelTest::transferValidation);
        System.out.println("All " + passed + " finance model tests passed.");
    }

    private static void accountTypeLabels() {
        assertEquals("Mobile Wallet", AccountType.MOBILE_WALLET.toString());
        assertEquals("Card", AccountType.CARD.getDisplayName());
    }

    private static void protectedDefault() {
        assertEquals("ACCOUNT_DEFAULT_CASH", Account.DEFAULT.getIdentifier());
        assertTrue(Account.DEFAULT.isProtected());
        assertTrue(Account.DEFAULT.isActive());
        assertMoney("0.00", Account.DEFAULT.getOpeningBalance());
    }

    private static void customNormalization() {
        Account account = account("ACCOUNT_BANK");
        assertEquals("Savings", account.getDisplayName());
        assertEquals(AccountType.BANK, account.getType());
        assertMoney("125.50", account.getOpeningBalance());
    }

    private static void renameKeepsId() {
        Account account = account("ACCOUNT_BANK");
        Account renamed = account.withDisplayName("Emergency");
        assertEquals(account.getIdentifier(), renamed.getIdentifier());
        assertEquals("Savings", account.getDisplayName());
        assertEquals("Emergency", renamed.getDisplayName());
    }

    private static void archiveIsImmutable() {
        Account account = account("ACCOUNT_BANK");
        Account archived = account.withArchived(true);
        assertTrue(account.isActive());
        assertTrue(archived.isArchived());
        assertEquals(account.getIdentifier(), archived.getIdentifier());
    }

    private static void protectedOperations() {
        expect(ValidationException.class,
                () -> Account.DEFAULT.withArchived(true));
        expect(ValidationException.class,
                () -> Account.DEFAULT.withDisplayName("Wallet"));
    }

    private static void invalidAccountValues() {
        expect(ValidationException.class, () -> Account.createCustom(
                "bad id", "Name", AccountType.CASH,
                BigDecimal.ZERO, false));
        expect(ValidationException.class, () -> Account.createCustom(
                "ACCOUNT_X", "\u2003", AccountType.CASH,
                BigDecimal.ZERO, false));
        expect(NullPointerException.class, () -> Account.createCustom(
                "ACCOUNT_X", "Name", null,
                BigDecimal.ZERO, false));
        expect(ValidationException.class, () -> Account.createCustom(
                "ACCOUNT_X", "Name", AccountType.CASH,
                new BigDecimal("0.001"), false));
    }

    private static void incomeGeneratedId() {
        Income income = income(Account.DEFAULT);
        assertTrue(income.getId().startsWith("INCOME_"));
        assertEquals(39, income.getId().length());
    }

    private static void incomeNormalization() {
        Income income = new Income(
                "INCOME_FIXED",
                LocalDate.now(),
                new BigDecimal("12.5"),
                "  Salary  ",
                Account.DEFAULT,
                "  July pay  ");
        assertMoney("12.50", income.getAmount());
        assertEquals("Salary", income.getSource());
        assertEquals("July pay", income.getNote());
        assertEquals("INCOME_FIXED", income.getId());
    }

    private static void incomeValidation() {
        expect(ValidationException.class, () -> new Income(
                LocalDate.now(), BigDecimal.ZERO,
                "Salary", Account.DEFAULT, ""));
        expect(ValidationException.class, () -> new Income(
                LocalDate.now().plusDays(1), BigDecimal.ONE,
                "Salary", Account.DEFAULT, ""));
        expect(NullPointerException.class, () -> new Income(
                LocalDate.now(), BigDecimal.ONE,
                "Salary", null, ""));
    }

    private static void transferGeneratedId() {
        Transfer transfer = transfer(
                Account.DEFAULT, account("ACCOUNT_BANK"));
        assertTrue(transfer.getId().startsWith("TRANSFER_"));
        assertEquals(41, transfer.getId().length());
    }

    private static void transferNormalization() {
        Account bank = account("ACCOUNT_BANK");
        Transfer transfer = new Transfer(
                "TRANSFER_FIXED",
                LocalDate.now(),
                new BigDecimal("25"),
                Account.DEFAULT,
                bank,
                "  Move funds  ");
        assertMoney("25.00", transfer.getAmount());
        assertEquals("Move funds", transfer.getNote());
        assertEquals(Account.DEFAULT, transfer.getSourceAccount());
        assertEquals(bank, transfer.getDestinationAccount());
    }

    private static void selfTransferRejection() {
        expect(ValidationException.class, () -> new Transfer(
                LocalDate.now(),
                BigDecimal.ONE,
                Account.DEFAULT,
                Account.DEFAULT,
                ""));
    }

    private static void transferValidation() {
        Account bank = account("ACCOUNT_BANK");
        expect(ValidationException.class, () -> new Transfer(
                LocalDate.now(), new BigDecimal("-1"),
                Account.DEFAULT, bank, ""));
        expect(ValidationException.class, () -> new Transfer(
                LocalDate.now().plusDays(1), BigDecimal.ONE,
                Account.DEFAULT, bank, ""));
        expect(NullPointerException.class, () -> new Transfer(
                LocalDate.now(), BigDecimal.ONE,
                null, bank, ""));
    }

    private static Account account(String id) {
        return Account.createCustom(
                id,
                "  Savings  ",
                AccountType.BANK,
                new BigDecimal("125.5"),
                false);
    }

    private static Income income(Account account) {
        return new Income(
                LocalDate.now(),
                new BigDecimal("10.00"),
                "Salary",
                account,
                "");
    }

    private static Transfer transfer(Account source, Account destination) {
        return new Transfer(
                LocalDate.now(),
                new BigDecimal("5.00"),
                source,
                destination,
                "");
    }

    private static void test(String name, Runnable test) {
        try {
            test.run();
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
        throw new AssertionError(
                "Expected " + expected.getSimpleName() + ".");
    }
}
