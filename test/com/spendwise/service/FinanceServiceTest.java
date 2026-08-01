package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.repository.TransferRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FinanceServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);
    private static int passed;

    private FinanceServiceTest() {
    }

    public static void main(String[] args) {
        test("account list default and immutable", FinanceServiceTest::accountList);
        test("account add stable ID", FinanceServiceTest::accountAdd);
        test("account duplicate names", FinanceServiceTest::accountDuplicate);
        test("account lifecycle keeps ID", FinanceServiceTest::accountLifecycle);
        test("default account protection", FinanceServiceTest::defaultProtection);
        test("unknown account rejection", FinanceServiceTest::unknownAccount);
        test("income create and list", FinanceServiceTest::incomeCreate);
        test("invalid income leaves repository", FinanceServiceTest::invalidIncome);
        test("income update replacement and position", FinanceServiceTest::incomeUpdate);
        test("missing income update", FinanceServiceTest::missingIncomeUpdate);
        test("income search and filters", FinanceServiceTest::incomeSearch);
        test("income stable sorting", FinanceServiceTest::incomeSorting);
        test("archived account rules", FinanceServiceTest::archivedRules);
        test("income delete behavior", FinanceServiceTest::incomeDelete);
        test("transfer create and list", FinanceServiceTest::transferCreate);
        test("invalid transfer is atomic", FinanceServiceTest::invalidTransfer);
        test("transfer update and delete", FinanceServiceTest::transferUpdateDelete);
        test("repository failures propagate", FinanceServiceTest::repositoryFailure);
        test("expense account update compatibility", FinanceServiceTest::expenseAccountUpdate);
        test("expense archived account rules",
                FinanceServiceTest::expenseArchivedRules);
        test("exact account balances", FinanceServiceTest::balances);
        test("transfers are total-neutral", FinanceServiceTest::transferNeutrality);
        test("balance snapshot is immutable", FinanceServiceTest::balanceImmutable);
        System.out.println(
                "All " + passed + " finance service tests passed.");
    }

    private static void accountList() {
        Fixture fixture = new Fixture();
        assertEquals(List.of(Account.DEFAULT),
                fixture.accountService.listAllAccounts());
        expect(UnsupportedOperationException.class,
                () -> fixture.accountService.listAllAccounts().add(Account.DEFAULT));
    }

    private static void accountAdd() {
        Fixture fixture = new Fixture();
        Account account = fixture.accountService.addAccount(
                "  Savings  ", AccountType.BANK, new BigDecimal("10"));
        assertTrue(account.getIdentifier().startsWith("ACCOUNT_"));
        assertEquals("Savings", account.getDisplayName());
        assertMoney("10.00", account.getOpeningBalance());
        assertEquals(account,
                fixture.accountService.resolveAccount(account.getIdentifier()));
    }

    private static void accountDuplicate() {
        Fixture fixture = new Fixture();
        fixture.accountService.addAccount(
                "Savings", AccountType.BANK, BigDecimal.ZERO);
        expect(ValidationException.class, () ->
            fixture.accountService.addAccount(
                    " savings ", AccountType.CASH, BigDecimal.ZERO));
        expect(ValidationException.class, () ->
            fixture.accountService.addAccount(
                    " cash ", AccountType.CASH, BigDecimal.ZERO));
        assertEquals(2, fixture.accountService.listAllAccounts().size());
    }

    private static void accountLifecycle() {
        Fixture fixture = new Fixture();
        Account account = fixture.addBank();
        Account renamed = fixture.accountService.renameAccount(
                account.getIdentifier(), "Reserve");
        Account archived = fixture.accountService.archiveAccount(
                account.getIdentifier());
        Account restored = fixture.accountService.restoreAccount(
                account.getIdentifier());
        assertEquals(account.getIdentifier(), renamed.getIdentifier());
        assertEquals(account.getIdentifier(), archived.getIdentifier());
        assertEquals(account.getIdentifier(), restored.getIdentifier());
        assertEquals("Reserve", restored.getDisplayName());
        assertTrue(restored.isActive());
    }

    private static void defaultProtection() {
        Fixture fixture = new Fixture();
        expect(ValidationException.class, () ->
            fixture.accountService.renameAccount(
                    Account.DEFAULT_IDENTIFIER, "Other"));
        expect(ValidationException.class, () ->
            fixture.accountService.archiveAccount(
                    Account.DEFAULT_IDENTIFIER));
    }

    private static void unknownAccount() {
        Fixture fixture = new Fixture();
        expect(RepositoryException.class, () ->
            fixture.accountService.resolveAccount("ACCOUNT_UNKNOWN"));
    }

    private static void incomeCreate() {
        Fixture fixture = new Fixture();
        Income income = fixture.incomeService.createIncome(
                DATE,
                new BigDecimal("500"),
                " Salary ",
                Account.DEFAULT,
                null);
        assertTrue(income.getId().startsWith("INCOME_"));
        assertEquals("Salary", income.getSource());
        assertMoney("500.00", income.getAmount());
        assertEquals(List.of(income), fixture.incomeService.getAllIncome());
        expect(UnsupportedOperationException.class,
                () -> fixture.incomeService.getAllIncome().clear());
    }

    private static void invalidIncome() {
        Fixture fixture = new Fixture();
        expect(ValidationException.class, () ->
            fixture.incomeService.createIncome(
                    DATE, BigDecimal.ZERO,
                    "Salary", Account.DEFAULT, ""));
        assertTrue(fixture.incomeRepository.entries.isEmpty());
    }

    private static void incomeUpdate() {
        Fixture fixture = new Fixture();
        Income first = fixture.incomeService.createIncome(
                DATE, new BigDecimal("10"), "First", Account.DEFAULT, "");
        Income second = fixture.incomeService.createIncome(
                DATE, new BigDecimal("20"), "Second", Account.DEFAULT, "");
        Income replacement = fixture.incomeService.updateIncome(
                first.getId(),
                DATE.plusDays(1),
                new BigDecimal("15"),
                "Updated",
                Account.DEFAULT,
                "note");
        assertTrue(first != replacement);
        assertEquals(first.getId(), replacement.getId());
        assertEquals(first.getId(),
                fixture.incomeService.getAllIncome().get(0).getId());
        assertEquals(second.getId(),
                fixture.incomeService.getAllIncome().get(1).getId());
        assertEquals("First", first.getSource());
    }

    private static void missingIncomeUpdate() {
        Fixture fixture = new Fixture();
        expect(FinanceNotFoundException.class, () ->
            fixture.incomeService.updateIncome(
                    "INCOME_MISSING",
                    DATE,
                    BigDecimal.ONE,
                    "Missing",
                    Account.DEFAULT,
                    ""));
        assertTrue(fixture.incomeRepository.entries.isEmpty());
    }

    private static void incomeSearch() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        fixture.incomeService.createIncome(
                DATE, new BigDecimal("10"), "Monthly Salary",
                Account.DEFAULT, "Employer");
        Income bonus = fixture.incomeService.createIncome(
                DATE.plusDays(2), new BigDecimal("20"), "Bonus",
                bank, "Performance");
        assertEquals(List.of(bonus), fixture.incomeService.findIncome(
                "PERFORMANCE",
                bank,
                DATE.plusDays(1),
                DATE.plusDays(2),
                IncomeSortOrder.ORIGINAL_ORDER));
        expect(ValidationException.class, () ->
            fixture.incomeService.findIncome(
                    "", null, DATE.plusDays(2), DATE,
                    IncomeSortOrder.ORIGINAL_ORDER));
    }

    private static void incomeSorting() {
        Fixture fixture = new Fixture();
        Income first = fixture.incomeService.createIncome(
                DATE, new BigDecimal("10"), "same",
                Account.DEFAULT, "");
        Income second = fixture.incomeService.createIncome(
                DATE.plusDays(1), new BigDecimal("10"), "Same",
                Account.DEFAULT, "");
        List<Income> sorted = fixture.incomeService.findIncome(
                null, null, null, null, IncomeSortOrder.SOURCE_A_TO_Z);
        assertEquals(List.of(first, second), sorted);
        assertEquals(List.of(first, second),
                fixture.incomeService.getAllIncome());
    }

    private static void archivedRules() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        Income income = fixture.incomeService.createIncome(
                DATE, BigDecimal.ONE, "Salary", bank, "");
        Account archived = fixture.accountService.archiveAccount(
                bank.getIdentifier());
        expect(ValidationException.class, () ->
            fixture.incomeService.createIncome(
                    DATE, BigDecimal.ONE, "New", archived, ""));
        Income updated = fixture.incomeService.updateIncome(
                income.getId(),
                DATE,
                new BigDecimal("2"),
                "Updated",
                archived,
                "");
        assertEquals(archived, updated.getAccount());
    }

    private static void incomeDelete() {
        Fixture fixture = new Fixture();
        Income income = fixture.incomeService.createIncome(
                DATE, BigDecimal.ONE, "Salary", Account.DEFAULT, "");
        assertTrue(fixture.incomeService.deleteIncome(income.getId()));
        assertFalse(fixture.incomeService.deleteIncome("INCOME_MISSING"));
    }

    private static void transferCreate() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        Transfer transfer = fixture.transferService.createTransfer(
                DATE,
                new BigDecimal("50"),
                Account.DEFAULT,
                bank,
                "Move");
        assertTrue(transfer.getId().startsWith("TRANSFER_"));
        assertEquals(List.of(transfer),
                fixture.transferService.getAllTransfers());
    }

    private static void invalidTransfer() {
        Fixture fixture = new Fixture();
        expect(ValidationException.class, () ->
            fixture.transferService.createTransfer(
                    DATE,
                    BigDecimal.ONE,
                    Account.DEFAULT,
                    Account.DEFAULT,
                    ""));
        assertTrue(fixture.transferRepository.entries.isEmpty());
    }

    private static void transferUpdateDelete() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        Transfer transfer = fixture.transferService.createTransfer(
                DATE, BigDecimal.ONE, Account.DEFAULT, bank, "");
        Transfer replacement = fixture.transferService.updateTransfer(
                transfer.getId(),
                DATE.plusDays(1),
                new BigDecimal("2"),
                bank,
                Account.DEFAULT,
                "reverse");
        assertTrue(transfer != replacement);
        assertEquals(transfer.getId(), replacement.getId());
        assertTrue(fixture.transferService.deleteTransfer(transfer.getId()));
        assertFalse(fixture.transferService.deleteTransfer("TRANSFER_MISSING"));
    }

    private static void repositoryFailure() {
        Fixture fixture = new Fixture();
        fixture.incomeRepository.failAdd = true;
        expect(RepositoryException.class, () ->
            fixture.incomeService.createIncome(
                    DATE, BigDecimal.ONE,
                    "Salary", Account.DEFAULT, ""));
        assertTrue(fixture.incomeRepository.entries.isEmpty());
    }

    private static void expenseAccountUpdate() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        Expense expense = fixture.expenseService.createExpense(
                "Lunch",
                new BigDecimal("12"),
                DATE,
                Category.FOOD,
                bank,
                "");
        Expense replacement = fixture.expenseService.updateExpense(
                expense.getId(),
                "Dinner",
                new BigDecimal("20"),
                DATE,
                Category.FOOD,
                "");
        assertEquals(bank, replacement.getAccount());
    }

    private static void expenseArchivedRules() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        Expense historical = fixture.expenseService.createExpense(
                "Lunch",
                BigDecimal.ONE,
                DATE,
                Category.FOOD,
                bank,
                "");
        Account archived = fixture.accountService.archiveAccount(
                bank.getIdentifier());

        expect(ValidationException.class, () ->
            fixture.expenseService.createExpense(
                    "New expense",
                    BigDecimal.ONE,
                    DATE,
                    Category.OTHER,
                    archived,
                    ""));
        Expense updated = fixture.expenseService.updateExpense(
                historical.getId(),
                "Updated lunch",
                new BigDecimal("2"),
                DATE,
                Category.FOOD,
                archived,
                "");

        assertEquals(1, fixture.expenseRepository.entries.size());
        assertEquals(archived, updated.getAccount());
    }

    private static void balances() {
        Fixture fixture = new Fixture();
        Account bank = fixture.accountService.addAccount(
                "Savings", AccountType.BANK, new BigDecimal("100"));
        fixture.incomeService.createIncome(
                DATE, new BigDecimal("1000"),
                "Salary", Account.DEFAULT, "");
        fixture.expenseService.createExpense(
                "Rent", new BigDecimal("200"), DATE,
                Category.BILLS, Account.DEFAULT, "");
        fixture.transferService.createTransfer(
                DATE, new BigDecimal("50"),
                Account.DEFAULT, bank, "");
        AccountBalanceSnapshot snapshot =
                fixture.financeService.calculateBalances();
        assertMoney("750.00", snapshot.getBalance(Account.DEFAULT));
        assertMoney("150.00", snapshot.getBalance(bank));
        assertMoney("900.00", snapshot.getTotalBalance());
    }

    private static void transferNeutrality() {
        Fixture fixture = new Fixture();
        Account bank = fixture.addBank();
        BigDecimal before =
                fixture.financeService.calculateBalances().getTotalBalance();
        fixture.transferService.createTransfer(
                DATE, new BigDecimal("25"),
                Account.DEFAULT, bank, "");
        BigDecimal after =
                fixture.financeService.calculateBalances().getTotalBalance();
        assertEquals(before, after);
    }

    private static void balanceImmutable() {
        Fixture fixture = new Fixture();
        AccountBalanceSnapshot snapshot =
                fixture.financeService.calculateBalances();
        expect(UnsupportedOperationException.class, () ->
            snapshot.getBalances().clear());
        assertEquals(2, snapshot.getTotalBalance().scale());
    }

    private static final class Fixture {

        private final MemoryAccountRepository accountRepository =
                new MemoryAccountRepository();
        private final MemoryIncomeRepository incomeRepository =
                new MemoryIncomeRepository();
        private final MemoryTransferRepository transferRepository =
                new MemoryTransferRepository();
        private final MemoryExpenseRepository expenseRepository =
                new MemoryExpenseRepository();
        private final AccountService accountService =
                new AccountService(accountRepository);
        private final IncomeService incomeService =
                new IncomeService(incomeRepository, accountService);
        private final TransferService transferService =
                new TransferService(transferRepository, accountService);
        private final ExpenseService expenseService =
                new ExpenseService(expenseRepository, accountService);
        private final FinanceService financeService =
                new FinanceService(
                        accountService,
                        expenseService,
                        incomeService,
                        transferService);

        private Account addBank() {
            return accountService.addAccount(
                    "Savings", AccountType.BANK, BigDecimal.ZERO);
        }
    }

    private static final class MemoryAccountRepository
            implements AccountRepository {

        private final List<Account> entries = new ArrayList<>();

        @Override
        public List<Account> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Account> findById(String id) {
            return entries.stream()
                    .filter(account -> account.getIdentifier().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Account account) {
            if (findById(account.getIdentifier()).isPresent()) {
                throw new RepositoryException("Duplicate account.");
            }
            entries.add(account);
        }

        @Override
        public void update(Account account) {
            int index = entries.indexOf(account);
            if (index < 0) {
                throw new RepositoryException("Missing account.");
            }
            entries.set(index, account);
        }
    }

    private static final class MemoryIncomeRepository
            implements IncomeRepository {

        private final List<Income> entries = new ArrayList<>();
        private boolean failAdd;

        @Override
        public List<Income> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Income> findById(String id) {
            return entries.stream()
                    .filter(income -> income.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Income income) {
            if (failAdd) {
                throw new RepositoryException("Simulated income failure.");
            }
            entries.add(income);
        }

        @Override
        public void update(Income income) {
            int index = entries.indexOf(income);
            if (index < 0) {
                throw new RepositoryException("Missing income.");
            }
            entries.set(index, income);
        }

        @Override
        public boolean deleteById(String id) {
            return entries.removeIf(income -> income.getId().equals(id));
        }
    }

    private static final class MemoryTransferRepository
            implements TransferRepository {

        private final List<Transfer> entries = new ArrayList<>();

        @Override
        public List<Transfer> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Transfer> findById(String id) {
            return entries.stream()
                    .filter(transfer -> transfer.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Transfer transfer) {
            entries.add(transfer);
        }

        @Override
        public void update(Transfer transfer) {
            int index = entries.indexOf(transfer);
            if (index < 0) {
                throw new RepositoryException("Missing transfer.");
            }
            entries.set(index, transfer);
        }

        @Override
        public boolean deleteById(String id) {
            return entries.removeIf(transfer -> transfer.getId().equals(id));
        }
    }

    private static final class MemoryExpenseRepository
            implements ExpenseRepository {

        private final List<Expense> entries = new ArrayList<>();

        @Override
        public List<Expense> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Expense> findById(String id) {
            return entries.stream()
                    .filter(expense -> expense.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Expense expense) {
            entries.add(expense);
        }

        @Override
        public void update(Expense expense) {
            int index = entries.indexOf(expense);
            if (index < 0) {
                throw new RepositoryException("Missing expense.");
            }
            entries.set(index, expense);
        }

        @Override
        public boolean deleteById(String id) {
            return entries.removeIf(expense -> expense.getId().equals(id));
        }
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

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false.");
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
