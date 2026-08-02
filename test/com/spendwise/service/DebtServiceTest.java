package com.spendwise.service;

import com.spendwise.model.DebtDirection;
import com.spendwise.model.DebtStatus;
import com.spendwise.repository.CsvDebtRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

public final class DebtServiceTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        new DebtServiceTest().run();
    }

    private void run() throws Exception {
        test("borrowed and lent persistence", this::persistence);
        test("partial paid and overdue status", this::status);
        test("repayment safety", this::repaymentSafety);
        test("debt ledger is memo only", this::memoOnly);
        System.out.println("All " + passed + " debt tests passed.");
    }

    private void persistence() throws Exception {
        withDirectory(directory -> {
            DebtService service = service(directory);
            var borrowed = service.addDebt(DebtDirection.BORROWED, "Bank",
                    money("1000"), LocalDate.of(2027, 1, 1), "Student loan");
            var lent = service.addDebt(DebtDirection.LENT, "Friend",
                    money("200"), LocalDate.of(2026, 12, 1), "Emergency");
            service.addRepayment(borrowed.getIdentifier(),
                    LocalDate.of(2025, 1, 1), money("100"), "First payment");
            byte[] before = Files.readAllBytes(directory.resolve("debts.csv"));
            DebtService restarted = service(directory);
            assertEquals(2, restarted.listProgress(
                    LocalDate.of(2026, 1, 1)).size());
            assertEquals(DebtDirection.LENT,
                    restarted.getProgress(lent.getIdentifier(),
                            LocalDate.of(2026, 1, 1)).debt().getDirection());
            assertArrayEquals(before, Files.readAllBytes(
                    directory.resolve("debts.csv")));
        });
    }

    private void status() throws Exception {
        withDirectory(directory -> {
            DebtService service = service(directory);
            var debt = service.addDebt(DebtDirection.BORROWED, "Bank",
                    money("100"), LocalDate.of(2025, 6, 1), "");
            assertEquals(DebtStatus.OPEN, service.getProgress(
                    debt.getIdentifier(), LocalDate.of(2025, 5, 1)).status());
            service.addRepayment(debt.getIdentifier(),
                    LocalDate.of(2025, 5, 2), money("40"), "");
            assertEquals(DebtStatus.PARTIALLY_REPAID, service.getProgress(
                    debt.getIdentifier(), LocalDate.of(2025, 5, 3)).status());
            assertEquals(DebtStatus.OVERDUE, service.getProgress(
                    debt.getIdentifier(), LocalDate.of(2025, 7, 1)).status());
            service.addRepayment(debt.getIdentifier(),
                    LocalDate.of(2025, 5, 4), money("60"), "");
            assertEquals(DebtStatus.PAID, service.getProgress(
                    debt.getIdentifier(), LocalDate.of(2025, 7, 1)).status());
        });
    }

    private void repaymentSafety() throws Exception {
        withDirectory(directory -> {
            DebtService service = service(directory);
            var debt = service.addDebt(DebtDirection.LENT, "Friend",
                    money("100"), LocalDate.of(2027, 1, 1), "");
            service.addRepayment(debt.getIdentifier(),
                    LocalDate.of(2025, 1, 1), money("80"), "");
            expect(ValidationException.class, () -> service.addRepayment(
                    debt.getIdentifier(), LocalDate.of(2025, 1, 2),
                    money("21"), "Overpayment"));
            expect(ValidationException.class, () -> service.updateDebt(
                    debt.getIdentifier(), DebtDirection.LENT, "Friend",
                    money("70"), LocalDate.of(2027, 1, 1), ""));
        });
    }

    private void memoOnly() throws Exception {
        withDirectory(directory -> {
            DebtService service = service(directory);
            var debt = service.addDebt(DebtDirection.BORROWED, "Family",
                    money("100"), LocalDate.of(2027, 1, 1), "");
            service.addRepayment(debt.getIdentifier(),
                    LocalDate.of(2025, 1, 1), money("10"), "Memo");
            assertTrue(Files.notExists(directory.resolve("expenses.csv")));
            assertTrue(Files.notExists(directory.resolve("income.csv")));
            assertTrue(Files.notExists(directory.resolve("transfers.csv")));
        });
    }

    private static DebtService service(Path directory) {
        return new DebtService(new CsvDebtRepository(
                directory.resolve("debts.csv")));
    }
    private static void withDirectory(DirectoryAction action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-debts-");
        try { action.run(directory); }
        finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
    private void test(String name, ThrowingRunnable action) throws Exception {
        try { action.run(); passed++; }
        catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }
    private static void expect(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected exception.", failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }
    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError("Expected equal bytes.");
        }
    }
    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }
    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface
    private interface DirectoryAction { void run(Path path) throws Exception; }
}
