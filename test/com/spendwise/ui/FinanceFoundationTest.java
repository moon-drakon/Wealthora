package com.spendwise.ui;

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
import com.spendwise.service.AccountService;
import com.spendwise.service.AccountStatementService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.FinanceService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.TransferService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class FinanceFoundationTest {

    private static final Account BANK = Account.createCustom(
            "ACCOUNT_BANK",
            "Savings",
            AccountType.BANK,
            new BigDecimal("100.00"),
            false);
    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);
    private static int passed;

    private FinanceFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        swingTest("account table foundation", FinanceFoundationTest::accountTable);
        swingTest("income table foundation", FinanceFoundationTest::incomeTable);
        swingTest("transfer table foundation", FinanceFoundationTest::transferTable);
        swingTest("finance panel initial refresh", FinanceFoundationTest::financePanel);
        swingTest("listener failure becomes refresh warning",
                FinanceFoundationTest::listenerWarning);
        swingTest("local refresh failure becomes warning",
                FinanceFoundationTest::localRefreshWarning);
        swingTest("income search refresh failure is contained",
                FinanceFoundationTest::incomeRefreshFailure);
        swingTest("expense listener failure becomes warning",
                FinanceFoundationTest::expenseListenerWarning);
        swingTest("table models reject editing", FinanceFoundationTest::modelsReadOnly);
        swingTest("account status filter", FinanceFoundationTest::accountFilter);
        swingTest("selected account statement", FinanceFoundationTest::accountStatement);
        swingTest("archived account excluded from expense choices",
                FinanceFoundationTest::expenseAccountChoices);
        System.out.println(
                "All " + passed + " finance Swing foundation tests passed.");
    }

    private static void accountTable() {
        AccountTableModel model = new AccountTableModel();
        LinkedHashMap<Account, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(Account.DEFAULT, new BigDecimal("-10.00"));
        balances.put(BANK, new BigDecimal("150.00"));
        model.replace(List.of(Account.DEFAULT, BANK), balances, BANK);
        assertEquals(2, model.getRowCount());
        assertEquals(11, model.getColumnCount());
        assertEquals("Account", model.getColumnName(0));
        assertEquals("Current Balance", model.getColumnName(5));
        assertEquals("Default", model.getColumnName(9));
        assertEquals("Savings", model.getValueAt(1, 0));
        assertEquals(new BigDecimal("150.00"), model.getValueAt(1, 5));
        assertEquals("Yes", model.getValueAt(1, 9));
        assertEquals("Protected", model.getValueAt(0, 10));
    }

    private static void incomeTable() {
        Income income = new Income(
                "INCOME_FIXED",
                DATE,
                new BigDecimal("25.00"),
                "Salary",
                BANK,
                "note");
        IncomeTableModel model = new IncomeTableModel();
        model.replace(List.of(income));
        assertEquals(1, model.getRowCount());
        assertEquals(DATE, model.getValueAt(0, 0));
        assertEquals("Salary", model.getValueAt(0, 1));
        assertEquals("Savings", model.getValueAt(0, 2));
        assertEquals(new BigDecimal("25.00"), model.getValueAt(0, 3));
    }

    private static void transferTable() {
        Transfer transfer = new Transfer(
                "TRANSFER_FIXED",
                DATE,
                new BigDecimal("10.00"),
                Account.DEFAULT,
                BANK,
                "move");
        TransferTableModel model = new TransferTableModel();
        model.replace(List.of(transfer));
        assertEquals(1, model.getRowCount());
        assertEquals("Cash", model.getValueAt(0, 1));
        assertEquals("Savings", model.getValueAt(0, 2));
        assertEquals(new BigDecimal("10.00"), model.getValueAt(0, 3));
    }

    private static void financePanel() {
        MemoryAccountRepository accounts = new MemoryAccountRepository();
        accounts.entries.add(BANK);
        MemoryIncomeRepository income = new MemoryIncomeRepository();
        income.entries.add(new Income(
                "INCOME_FIXED", DATE, BigDecimal.ONE,
                "Salary", BANK, ""));
        MemoryTransferRepository transfers = new MemoryTransferRepository();
        transfers.entries.add(new Transfer(
                "TRANSFER_FIXED", DATE, BigDecimal.ONE,
                Account.DEFAULT, BANK, ""));
        MemoryExpenseRepository expenses = new MemoryExpenseRepository();
        expenses.entries.add(new Expense(
                "expense-fixed", "Lunch", BigDecimal.ONE,
                DATE, Category.FOOD, Account.DEFAULT, ""));

        AccountService accountService = new AccountService(accounts);
        IncomeService incomeService =
                new IncomeService(income, accountService);
        TransferService transferService =
                new TransferService(transfers, accountService);
        ExpenseService expenseService =
                new ExpenseService(expenses, accountService);
        FinancePanel panel = new FinancePanel(
                accountService,
                incomeService,
                transferService,
                new FinanceService(
                        accountService,
                        expenseService,
                        incomeService,
                        transferService),
                () -> {
                });
        assertEquals(2, panel.getAccountRowCount());
        assertEquals(1, panel.getIncomeRowCount());
        assertEquals(1, panel.getTransferRowCount());
        assertEquals(0, accounts.mutationCount);
        assertEquals(0, income.mutationCount);
        assertEquals(0, transfers.mutationCount);
        assertEquals(0, expenses.mutationCount);
    }

    private static void modelsReadOnly() {
        AccountTableModel accounts = new AccountTableModel();
        accounts.replace(
                List.of(Account.DEFAULT),
                java.util.Map.of(Account.DEFAULT, BigDecimal.ZERO.setScale(2)));
        IncomeTableModel income = new IncomeTableModel();
        income.replace(List.of());
        TransferTableModel transfers = new TransferTableModel();
        transfers.replace(List.of());
        assertFalse(accounts.isCellEditable(0, 0));
        assertFalse(income.isCellEditable(0, 0));
        assertFalse(transfers.isCellEditable(0, 0));
    }

    private static void listenerWarning() {
        PanelFixture fixture = new PanelFixture(() -> {
            throw new RepositoryException("Simulated listener failure.");
        });
        fixture.panel.mutationSucceeded("Income saved.");
        assertContains(fixture.panel.getStatusText(), "Income saved.");
        assertContains(fixture.panel.getStatusText(), "could not refresh");
    }

    private static void localRefreshWarning() {
        PanelFixture fixture = new PanelFixture(() -> {
        });
        int previousRows = fixture.panel.getAccountRowCount();
        fixture.accounts.entries.add(BANK);
        fixture.accounts.failReads = true;
        fixture.panel.mutationSucceeded("Account saved.");
        assertContains(fixture.panel.getStatusText(), "Account saved.");
        assertContains(fixture.panel.getStatusText(), "could not refresh");
        assertEquals(previousRows, fixture.panel.getAccountRowCount());
    }

    private static void incomeRefreshFailure() {
        PanelFixture fixture = new PanelFixture(() -> {
        });
        fixture.income.failReads = true;

        assertFalse(fixture.panel.refreshIncomeData());

        assertContains(fixture.panel.getStatusText(),
                "Unable to load income data");
    }

    private static void expenseListenerWarning() {
        MemoryExpenseRepository expenses = new MemoryExpenseRepository();
        ExpensePanel panel = new ExpensePanel(
                new ExpenseService(expenses),
                null,
                null,
                category -> false,
                () -> {
                },
                () -> {
                    throw new RepositoryException(
                            "Simulated finance refresh failure.");
                });

        panel.refreshAfterExpenseMutation("Expense saved.");

        assertContains(panel.getStatusText(), "Expense saved.");
        assertContains(panel.getStatusText(), "could not refresh");
    }

    private static void accountFilter() {
        PanelFixture fixture = new PanelFixture(() -> {
        });
        fixture.accounts.entries.add(BANK.withArchived(true));
        fixture.panel.refreshFinanceData();
        assertEquals(2, fixture.panel.getAccountRowCount());
        fixture.panel.setAccountFilter("Active accounts");
        assertEquals(1, fixture.panel.getAccountRowCount());
        fixture.panel.setAccountFilter("Archived accounts");
        assertEquals(1, fixture.panel.getAccountRowCount());
        fixture.panel.setAccountFilter("All accounts");
        assertEquals(2, fixture.panel.getAccountRowCount());
    }

    private static void accountStatement() {
        PanelFixture fixture = new PanelFixture(() -> {
        });
        fixture.accounts.entries.add(BANK);
        fixture.expenses.entries.add(new Expense(
                "expense-statement", "Lunch", new BigDecimal("20.00"),
                DATE, Category.FOOD, BANK, ""));
        fixture.income.entries.add(new Income(
                "INCOME_STATEMENT", DATE, new BigDecimal("50.00"),
                "Salary", BANK, ""));
        fixture.transfers.entries.add(new Transfer(
                "TRANSFER_STATEMENT", DATE, new BigDecimal("10.00"),
                BANK, Account.DEFAULT, "Move"));
        fixture.panel.refreshFinanceData();
        fixture.panel.selectAccount(BANK.getIdentifier());
        assertEquals(3, fixture.panel.getStatementRowCount());
        assertContains(fixture.panel.getStatementSummaryText(),
                "Income: 50.00");
        assertContains(fixture.panel.getStatementSummaryText(),
                "Expenses: 20.00");
        assertContains(fixture.panel.getStatementSummaryText(),
                "Current: 120.00");
    }

    private static void expenseAccountChoices() {
        MemoryAccountRepository accounts = new MemoryAccountRepository();
        accounts.entries.add(BANK.withArchived(true));
        AccountService accountService = new AccountService(accounts);
        ExpensePanel panel = new ExpensePanel(
                new ExpenseService(new MemoryExpenseRepository(), accountService),
                null,
                accountService,
                category -> false,
                () -> {
                },
                () -> {
                });
        assertEquals(List.of(Account.DEFAULT),
                panel.getSelectableAccountSnapshot(null));
    }

    private static final class PanelFixture {

        private final MemoryAccountRepository accounts =
                new MemoryAccountRepository();
        private final MemoryIncomeRepository income =
                new MemoryIncomeRepository();
        private final MemoryTransferRepository transfers =
                new MemoryTransferRepository();
        private final MemoryExpenseRepository expenses =
                new MemoryExpenseRepository();
        private final FinancePanel panel;

        private PanelFixture(Runnable listener) {
            AccountService accountService = new AccountService(accounts);
            IncomeService incomeService =
                    new IncomeService(income, accountService);
            TransferService transferService =
                    new TransferService(transfers, accountService);
            ExpenseService expenseService =
                    new ExpenseService(expenses, accountService);
            FinanceService financeService = new FinanceService(
                    accountService,
                    expenseService,
                    incomeService,
                    transferService);
            panel = new FinancePanel(
                    accountService,
                    incomeService,
                    transferService,
                    financeService,
                    new AccountStatementService(
                            accountService,
                            expenseService,
                            incomeService,
                            transferService,
                            financeService),
                    listener);
        }
    }

    private static void swingTest(String name, Runnable action)
            throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(name + " failed", failure.get());
        }
        passed++;
    }

    private static final class MemoryAccountRepository
            implements AccountRepository {

        private final List<Account> entries = new ArrayList<>();
        private int mutationCount;
        private boolean failReads;

        @Override
        public List<Account> findAll() {
            if (failReads) {
                throw new RepositoryException(
                        "Simulated account refresh failure.");
            }
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
            mutationCount++;
            entries.add(account);
        }

        @Override
        public void update(Account account) {
            mutationCount++;
            entries.set(entries.indexOf(account), account);
        }
    }

    private static final class MemoryIncomeRepository
            implements IncomeRepository {

        private final List<Income> entries = new ArrayList<>();
        private int mutationCount;
        private boolean failReads;

        @Override
        public List<Income> findAll() {
            if (failReads) {
                throw new RepositoryException(
                        "Simulated income refresh failure.");
            }
            return List.copyOf(entries);
        }

        @Override
        public Optional<Income> findById(String id) {
            return entries.stream()
                    .filter(entry -> entry.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Income entry) {
            mutationCount++;
            entries.add(entry);
        }

        @Override
        public void update(Income entry) {
            mutationCount++;
            entries.set(entries.indexOf(entry), entry);
        }

        @Override
        public boolean deleteById(String id) {
            mutationCount++;
            return entries.removeIf(entry -> entry.getId().equals(id));
        }
    }

    private static final class MemoryTransferRepository
            implements TransferRepository {

        private final List<Transfer> entries = new ArrayList<>();
        private int mutationCount;

        @Override
        public List<Transfer> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Transfer> findById(String id) {
            return entries.stream()
                    .filter(entry -> entry.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Transfer entry) {
            mutationCount++;
            entries.add(entry);
        }

        @Override
        public void update(Transfer entry) {
            mutationCount++;
            entries.set(entries.indexOf(entry), entry);
        }

        @Override
        public boolean deleteById(String id) {
            mutationCount++;
            return entries.removeIf(entry -> entry.getId().equals(id));
        }
    }

    private static final class MemoryExpenseRepository
            implements ExpenseRepository {

        private final List<Expense> entries = new ArrayList<>();
        private int mutationCount;

        @Override
        public List<Expense> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Expense> findById(String id) {
            return entries.stream()
                    .filter(entry -> entry.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Expense entry) {
            mutationCount++;
            entries.add(entry);
        }

        @Override
        public void update(Expense entry) {
            mutationCount++;
            entries.set(entries.indexOf(entry), entry);
        }

        @Override
        public boolean deleteById(String id) {
            mutationCount++;
            return entries.removeIf(entry -> entry.getId().equals(id));
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

    private static void assertContains(String actual, String expectedPart) {
        if (actual == null || !actual.contains(expectedPart)) {
            throw new AssertionError(
                    "Expected <" + actual + "> to contain <"
                    + expectedPart + ">.");
        }
    }
}
