package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvSavingsGoalRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

public final class SavingsGoalServiceTest {
    private int passed;

    public static void main(String[] args) throws Exception {
        new SavingsGoalServiceTest().run();
    }

    private void run() throws Exception {
        test("goal and contribution persistence", this::persistence);
        test("progress and achieved state", this::progress);
        test("archive and validation safety", this::archiveSafety);
        test("contributions do not mutate transactions", this::memoOnly);
        System.out.println("All " + passed + " savings goal tests passed.");
    }

    private void persistence() throws Exception {
        withFixture(fixture -> {
            var goal = fixture.service.addGoal("Laptop", money("1000"),
                    LocalDate.of(2027, 1, 1), fixture.account);
            fixture.service.addContribution(goal.getIdentifier(),
                    LocalDate.of(2025, 1, 2), money("100"), "First deposit");
            byte[] before = Files.readAllBytes(
                    fixture.directory.resolve("savings-goals.csv"));
            Fixture restarted = new Fixture(fixture.directory);
            assertEquals(goal.getIdentifier(),
                    restarted.service.listGoals().get(0).getIdentifier());
            assertEquals("First deposit", restarted.service
                    .getProgress(goal.getIdentifier()).contributions()
                    .get(0).getNote());
            assertArrayEquals(before, Files.readAllBytes(
                    fixture.directory.resolve("savings-goals.csv")));
        });
    }

    private void progress() throws Exception {
        withFixture(fixture -> {
            var goal = fixture.service.addGoal("Emergency", money("500"),
                    LocalDate.of(2027, 1, 1), fixture.account);
            fixture.service.addContribution(goal.getIdentifier(),
                    LocalDate.of(2025, 1, 1), money("200"), "");
            SavingsGoalProgress first = fixture.service.getProgress(
                    goal.getIdentifier());
            assertMoney("200.00", first.contributedAmount());
            assertMoney("300.00", first.remainingAmount());
            assertMoney("40.00", first.progressPercentage());
            assertTrue(!first.achieved());
            fixture.service.addContribution(goal.getIdentifier(),
                    LocalDate.of(2025, 1, 2), money("350"), "");
            SavingsGoalProgress achieved = fixture.service.getProgress(
                    goal.getIdentifier());
            assertTrue(achieved.achieved());
            assertMoney("0.00", achieved.remainingAmount());
        });
    }

    private void archiveSafety() throws Exception {
        withFixture(fixture -> {
            var goal = fixture.service.addGoal("Trip", money("200"),
                    LocalDate.of(2027, 1, 1), fixture.account);
            fixture.service.setActive(goal.getIdentifier(), false);
            expect(ValidationException.class, () ->
                    fixture.service.addContribution(goal.getIdentifier(),
                            LocalDate.of(2025, 1, 1), money("10"), ""));
            fixture.service.addGoal("Other", money("200"),
                    LocalDate.of(2027, 1, 1), fixture.account);
            expect(ValidationException.class, () -> fixture.service.addGoal(
                    "Other", money("300"), LocalDate.of(2028, 1, 1),
                    fixture.account));
        });
    }

    private void memoOnly() throws Exception {
        withFixture(fixture -> {
            var goal = fixture.service.addGoal("Memo", money("200"),
                    LocalDate.of(2027, 1, 1), fixture.account);
            fixture.service.addContribution(goal.getIdentifier(),
                    LocalDate.of(2025, 1, 1), money("10"), "Recorded only");
            assertTrue(Files.notExists(fixture.directory.resolve("expenses.csv")));
            assertTrue(Files.notExists(fixture.directory.resolve("income.csv")));
            assertTrue(Files.notExists(fixture.directory.resolve("transfers.csv")));
        });
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-goals-");
        try { action.run(new Fixture(directory)); }
        finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final class Fixture {
        private final Path directory;
        private final Account account;
        private final SavingsGoalService service;

        Fixture(Path directory) {
            this.directory = directory;
            AccountService accounts = new AccountService(
                    new CsvAccountRepository(directory.resolve("accounts.csv")));
            account = accounts.listAllAccounts().stream()
                    .filter(item -> !item.isProtected())
                    .findFirst()
                    .orElseGet(() -> accounts.addAccount(
                            "Savings", AccountType.SAVINGS, money("0")));
            service = new SavingsGoalService(new CsvSavingsGoalRepository(
                    directory.resolve("savings-goals.csv"),
                    accounts::resolveAccount), accounts);
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
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(money(expected), actual);
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
    private interface FixtureAction { void run(Fixture fixture) throws Exception; }
}
