package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.repository.RepositoryException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;

public final class PresentationDataServiceTest {

    private int passed;

    public static void main(String[] args) throws Exception {
        new PresentationDataServiceTest().run();
    }

    private void run() throws Exception {
        test("load is idempotent and removal is exact",
                this::idempotentLoadAndExactRemoval);
        test("manual account collision is preserved",
                this::manualAccountCollision);
        test("preexisting seeded identifier is preserved",
                this::preexistingIdentifier);
        test("invalid manifest fails closed", this::invalidManifest);
        System.out.println("All " + passed
                + " presentation-data safety tests passed.");
    }

    private void idempotentLoadAndExactRemoval() throws Exception {
        withFixture(fixture -> {
            PresentationDataService.PresentationDataResult first =
                    fixture.presentation.load();
            assertEquals(49, first.total());
            assertEquals(3, first.accounts());
            assertTrue(fixture.presentation.isLoaded());
            assertEquals(0, fixture.presentation.load().total());

            PresentationDataService.PresentationDataResult removed =
                    fixture.presentation.remove();
            assertEquals(49, removed.total());
            assertFalse(fixture.presentation.isLoaded());
            assertEquals(0, fixture.income.getAllIncome().size());
            assertEquals(0, fixture.expenses.getAllExpenses().size());
            assertEquals(0, fixture.transfers.getAllTransfers().size());
            assertEquals(1L, fixture.accounts.listAllAccounts().stream()
                    .filter(Account::isActive).count());
            assertFalse(Files.exists(fixture.manifest));
        });
    }

    private void manualAccountCollision() throws Exception {
        withFixture(fixture -> {
            Account manual = fixture.accounts.addAccount(
                    "City Bank", AccountType.BANK,
                    new BigDecimal("900.00"));
            PresentationDataService.PresentationDataResult loaded =
                    fixture.presentation.load();
            assertEquals(2, loaded.accounts());
            fixture.presentation.remove();
            Account preserved = fixture.accounts.listAllAccounts().stream()
                    .filter(account -> account.getIdentifier()
                            .equals(manual.getIdentifier()))
                    .findFirst().orElseThrow();
            assertTrue(preserved.isActive());
            assertEquals("City Bank", preserved.getDisplayName());
        });
    }

    private void preexistingIdentifier() throws Exception {
        withFixture(fixture -> {
            fixture.income.createIncomeWithId(
                    "INCOME_PRESENTATION01", LocalDate.now().minusDays(2),
                    new BigDecimal("77.00"), "Manual entry", Account.DEFAULT,
                    "Must remain");
            fixture.presentation.load();
            fixture.presentation.remove();
            assertTrue(fixture.income.findById("INCOME_PRESENTATION01")
                    .isPresent());
            assertEquals("Must remain", fixture.income
                    .findById("INCOME_PRESENTATION01").orElseThrow().getNote());
        });
    }

    private void invalidManifest() throws Exception {
        withFixture(fixture -> {
            Files.createDirectories(fixture.manifest.getParent());
            Files.writeString(fixture.manifest,
                    "format=1\nincome=INCOME_NOT_ALLOWED\n");
            expect(RepositoryException.class, fixture.presentation::remove);
            assertEquals(0, fixture.income.getAllIncome().size());
        });
    }

    private void test(String name, ThrowingAction action) throws Exception {
        try {
            action.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Throwable failure) {
            throw new AssertionError("FAIL: " + name, failure);
        }
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path root = Files.createTempDirectory("wealthora-presentation-");
        try {
            action.run(new Fixture(root));
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void expect(
            Class<? extends Throwable> type, ThrowingAction action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Unexpected exception", failure);
            }
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected false.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    private record Fixture(
            AccountService accounts,
            ExpenseService expenses,
            IncomeService income,
            TransferService transfers,
            Path manifest,
            PresentationDataService presentation) {

        Fixture(Path root) {
            this(create(root), root.resolve("presentation-manifest.properties"));
        }

        private Fixture(FinanceWorkspace workspace, Path manifest) {
            this(new AccountService(workspace.accounts(),
                            workspace.accountPreference()),
                    workspace, manifest);
        }

        private Fixture(
                AccountService accountService,
                FinanceWorkspace workspace,
                Path manifest) {
            this(accountService,
                    new ExpenseService(workspace.expenses(), accountService),
                    new IncomeService(workspace.income(), accountService),
                    new TransferService(workspace.transfers(), accountService),
                    manifest);
        }

        private Fixture(
                AccountService accounts,
                ExpenseService expenses,
                IncomeService income,
                TransferService transfers,
                Path manifest) {
            this(accounts, expenses, income, transfers, manifest,
                    new PresentationDataService(accounts, expenses, income,
                            transfers, manifest));
        }

        private static FinanceWorkspace create(Path root) {
            return FinanceWorkspace.overDirectory(root.resolve("data"));
        }
    }

    @FunctionalInterface
    private interface FixtureAction {
        void run(Fixture fixture) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
