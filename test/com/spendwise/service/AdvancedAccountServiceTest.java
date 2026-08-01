package com.spendwise.service;

import com.spendwise.config.AppPaths;
import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import com.spendwise.repository.AccountPreferenceRepository;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.CsvAccountPreferenceRepository;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.repository.TransferRepository;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdvancedAccountServiceTest {

    private static final Account BANK = Account.createCustom(
            "ACCOUNT_ADVANCED_BANK",
            "Savings",
            AccountType.BANK,
            new BigDecimal("100.00"),
            false);
    private static final Account WALLET = Account.createCustom(
            "ACCOUNT_ADVANCED_WALLET",
            "Wallet",
            AccountType.CASH,
            new BigDecimal("5.00"),
            false);
    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);
    private static Map<String, String> productionBefore;
    private static int passed;

    private AdvancedAccountServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        productionBefore = productionFingerprint();
        test("account rename preserves stable ID",
                AdvancedAccountServiceTest::renameStableId);
        test("metadata edit preserves opening balance",
                AdvancedAccountServiceTest::metadataEdit);
        test("duplicate active name rejected",
                AdvancedAccountServiceTest::duplicateName);
        test("Cash is fallback default without a settings write",
                AdvancedAccountServiceTest::fallbackDefault);
        test("default selection persists",
                AdvancedAccountServiceTest::defaultPersistence);
        test("archived account cannot become default",
                AdvancedAccountServiceTest::archivedDefaultRejected);
        test("archiving default chooses active replacement",
                AdvancedAccountServiceTest::replacementDefault);
        test("account archive and restore preserve ID",
                AdvancedAccountServiceTest::archiveRestore);
        test("protected Cash guarantees an active account",
                AdvancedAccountServiceTest::lastActiveProtection);
        test("historical entries survive account archival",
                AdvancedAccountServiceTest::historicalCompatibility);
        test("archived account rejected for a new expense",
                AdvancedAccountServiceTest::archivedExpenseRejected);
        test("archived account rejected for new income",
                AdvancedAccountServiceTest::archivedIncomeRejected);
        test("invalid archived and same-account transfers rejected",
                AdvancedAccountServiceTest::invalidTransfers);
        test("balance exact after rename and archive",
                AdvancedAccountServiceTest::balanceAfterLifecycle);
        test("account statement totals and activity are exact",
                AdvancedAccountServiceTest::statementTotals);
        test("existing account CSV remains backward compatible",
                AdvancedAccountServiceTest::csvCompatibility);
        test("production data remains untouched",
                AdvancedAccountServiceTest::productionUntouched);
        System.out.println("All " + passed
                + " advanced account service tests passed.");
    }

    private static void renameStableId() {
        Fixture fixture = populatedFixture();
        Account renamed = fixture.accountService.renameAccount(
                BANK.getIdentifier(), "Emergency Savings");
        assertEquals(BANK.getIdentifier(), renamed.getIdentifier());
        assertEquals("Emergency Savings", renamed.getDisplayName());
    }

    private static void metadataEdit() {
        Fixture fixture = populatedFixture();
        Account updated = fixture.accountService.updateAccountMetadata(
                BANK.getIdentifier(), "Daily Card", AccountType.CARD);
        assertEquals(AccountType.CARD, updated.getType());
        assertMoney("100.00", updated.getOpeningBalance());
    }

    private static void duplicateName() {
        Fixture fixture = populatedFixture();
        expect(ValidationException.class, () ->
            fixture.accountService.updateAccountMetadata(
                    BANK.getIdentifier(), " wallet ", AccountType.BANK));
        assertEquals("Savings", fixture.accountService
                .resolveAccount(BANK.getIdentifier()).getDisplayName());
    }

    private static void fallbackDefault() {
        Fixture fixture = populatedFixture();
        assertEquals(Account.DEFAULT, fixture.accountService.getDefaultAccount());
        assertTrue(fixture.preferences.identifier.isEmpty());
    }

    private static void defaultPersistence() {
        Fixture fixture = populatedFixture();
        fixture.accountService.setDefaultAccount(BANK.getIdentifier());
        AccountService restarted = new AccountService(
                fixture.accounts, fixture.preferences);
        assertEquals(BANK, restarted.getDefaultAccount());
        assertEquals(BANK.getIdentifier(),
                fixture.preferences.identifier.orElseThrow());
    }

    private static void archivedDefaultRejected() {
        Fixture fixture = populatedFixture();
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        expect(ValidationException.class, () ->
            fixture.accountService.setDefaultAccount(BANK.getIdentifier()));
    }

    private static void replacementDefault() {
        Fixture fixture = populatedFixture();
        fixture.accountService.setDefaultAccount(BANK.getIdentifier());
        AccountArchiveResult result = fixture.accountService
                .archiveAccountWithResult(BANK.getIdentifier());
        assertEquals(Account.DEFAULT,
                result.replacementDefault().orElseThrow());
        assertEquals(Account.DEFAULT, fixture.accountService.getDefaultAccount());
        assertTrue(result.archivedAccount().isArchived());
    }

    private static void archiveRestore() {
        Fixture fixture = populatedFixture();
        Account archived = fixture.accountService.archiveAccount(
                WALLET.getIdentifier());
        Account restored = fixture.accountService.restoreAccount(
                WALLET.getIdentifier());
        assertEquals(WALLET.getIdentifier(), archived.getIdentifier());
        assertEquals(WALLET.getIdentifier(), restored.getIdentifier());
        assertTrue(archived.isArchived());
        assertTrue(restored.isActive());
    }

    private static void lastActiveProtection() {
        Fixture fixture = populatedFixture();
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        fixture.accountService.archiveAccount(WALLET.getIdentifier());
        assertEquals(List.of(Account.DEFAULT),
                fixture.accountService.listSelectableAccounts());
        expect(ValidationException.class, () ->
            fixture.accountService.archiveAccount(Account.DEFAULT_IDENTIFIER));
    }

    private static void historicalCompatibility() {
        Fixture fixture = populatedFixture();
        fixture.expenseService.createExpense(
                "Lunch", money("20.00"), DATE, Category.FOOD, BANK, "");
        fixture.incomeService.createIncome(
                DATE, money("50.00"), "Pay", BANK, "");
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        assertEquals(BANK, fixture.expenses.entries.get(0).getAccount());
        assertEquals(BANK, fixture.income.entries.get(0).getAccount());
        assertEquals(2, fixture.statementService.getStatement(
                BANK.getIdentifier()).getEntries().size());
    }

    private static void archivedExpenseRejected() {
        Fixture fixture = populatedFixture();
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        expect(ValidationException.class, () ->
            fixture.expenseService.createExpense(
                    "Blocked", money("1.00"), DATE,
                    Category.FOOD, BANK, ""));
        assertTrue(fixture.expenses.entries.isEmpty());
    }

    private static void archivedIncomeRejected() {
        Fixture fixture = populatedFixture();
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        expect(ValidationException.class, () ->
            fixture.incomeService.createIncome(
                    DATE, money("1.00"), "Blocked", BANK, ""));
        assertTrue(fixture.income.entries.isEmpty());
    }

    private static void invalidTransfers() {
        Fixture fixture = populatedFixture();
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        expect(ValidationException.class, () ->
            fixture.transferService.createTransfer(
                    DATE, money("1.00"), BANK, Account.DEFAULT, ""));
        expect(ValidationException.class, () ->
            fixture.transferService.createTransfer(
                    DATE, money("1.00"), Account.DEFAULT,
                    Account.DEFAULT, ""));
        assertTrue(fixture.transfers.entries.isEmpty());
    }

    private static void balanceAfterLifecycle() {
        Fixture fixture = ledgerFixture();
        fixture.accountService.updateAccountMetadata(
                BANK.getIdentifier(), "Reserve", AccountType.CARD);
        fixture.accountService.archiveAccount(BANK.getIdentifier());
        Account current = fixture.accountService.resolveAccount(
                BANK.getIdentifier());
        assertMoney("160.00", fixture.financeService
                .calculateBalances().getBalance(current));
    }

    private static void statementTotals() {
        Fixture fixture = ledgerFixture();
        AccountStatementSnapshot statement = fixture.statementService
                .getStatement(BANK.getIdentifier());
        assertMoney("100.00", statement.getOpeningBalance());
        assertMoney("50.00", statement.getIncome());
        assertMoney("20.00", statement.getExpenses());
        assertMoney("40.00", statement.getIncomingTransfers());
        assertMoney("10.00", statement.getOutgoingTransfers());
        assertMoney("160.00", statement.getCurrentBalance());
        assertEquals(4, statement.getEntries().size());
    }

    private static void csvCompatibility() throws Exception {
        Path directory = Files.createTempDirectory("spendwise-account-compat-");
        try {
            Path accountsPath = directory.resolve("accounts.csv");
            Path settingsPath = directory.resolve("account-settings.csv");
            Files.writeString(
                    accountsPath,
                    CsvAccountRepository.HEADER + "\n"
                    + "ACCOUNT_LEGACY,Legacy,BANK,12.34,ACTIVE\n",
                    StandardCharsets.UTF_8);
            AccountService service = new AccountService(
                    new CsvAccountRepository(accountsPath),
                    new CsvAccountPreferenceRepository(settingsPath));
            assertEquals("Legacy", service.resolveAccount(
                    "ACCOUNT_LEGACY").getDisplayName());
            assertEquals(Account.DEFAULT, service.getDefaultAccount());
            assertFalse(Files.exists(settingsPath));
            service.renameAccount("ACCOUNT_LEGACY", "Legacy Savings");
            assertTrue(Files.readString(accountsPath, StandardCharsets.UTF_8)
                    .startsWith(CsvAccountRepository.HEADER + "\n"));
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void productionUntouched() throws Exception {
        assertEquals(productionBefore, productionFingerprint());
    }

    private static Fixture populatedFixture() {
        Fixture fixture = new Fixture();
        fixture.accounts.add(BANK);
        fixture.accounts.add(WALLET);
        return fixture;
    }

    private static Fixture ledgerFixture() {
        Fixture fixture = populatedFixture();
        fixture.expenseService.createExpense(
                "Food", money("20.00"), DATE, Category.FOOD, BANK, "");
        fixture.incomeService.createIncome(
                DATE, money("50.00"), "Salary", BANK, "");
        fixture.transferService.createTransfer(
                DATE, money("40.00"), Account.DEFAULT, BANK, "Deposit");
        fixture.transferService.createTransfer(
                DATE, money("10.00"), BANK, Account.DEFAULT, "Withdraw");
        return fixture;
    }

    private static Map<String, String> productionFingerprint()
            throws IOException, NoSuchAlgorithmException {
        Path directory = AppPaths.getExpenseCsvPath().getParent();
        Map<String, String> result = new LinkedHashMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String fileName : ManagedDataFiles.FILE_NAMES) {
            Path path = directory.resolve(fileName);
            result.put(fileName, Files.exists(path)
                    ? java.util.HexFormat.of().formatHex(
                            digest.digest(Files.readAllBytes(path)))
                    : "MISSING");
            digest.reset();
        }
        return Map.copyOf(result);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void test(String name, ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static <T extends Throwable> void expect(
            Class<T> expected, ThrowingRunnable action) {
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

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class Fixture {
        private final MemoryAccountRepository accounts =
                new MemoryAccountRepository();
        private final MemoryPreferenceRepository preferences =
                new MemoryPreferenceRepository();
        private final MemoryExpenseRepository expenses =
                new MemoryExpenseRepository();
        private final MemoryIncomeRepository income =
                new MemoryIncomeRepository();
        private final MemoryTransferRepository transfers =
                new MemoryTransferRepository();
        private final AccountService accountService =
                new AccountService(accounts, preferences);
        private final ExpenseService expenseService =
                new ExpenseService(expenses, accountService);
        private final IncomeService incomeService =
                new IncomeService(income, accountService);
        private final TransferService transferService =
                new TransferService(transfers, accountService);
        private final FinanceService financeService = new FinanceService(
                accountService,
                expenseService,
                incomeService,
                transferService);
        private final AccountStatementService statementService =
                new AccountStatementService(
                        accountService,
                        expenseService,
                        incomeService,
                        transferService,
                        financeService);
    }

    private static final class MemoryAccountRepository
            implements AccountRepository {
        private final List<Account> entries = new ArrayList<>();

        @Override
        public List<Account> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<Account> findById(String identifier) {
            return entries.stream().filter(account ->
                account.getIdentifier().equals(identifier)).findFirst();
        }

        @Override
        public void add(Account account) {
            entries.add(account);
        }

        @Override
        public void update(Account account) {
            int index = -1;
            for (int candidate = 0; candidate < entries.size(); candidate++) {
                if (entries.get(candidate).equals(account)) {
                    index = candidate;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalStateException("Missing account in test repository.");
            }
            entries.set(index, account);
        }
    }

    private static final class MemoryPreferenceRepository
            implements AccountPreferenceRepository {
        private Optional<String> identifier = Optional.empty();

        @Override
        public Optional<String> findDefaultAccountId() {
            return identifier;
        }

        @Override
        public void saveDefaultAccountId(String accountIdentifier) {
            identifier = Optional.of(accountIdentifier);
        }
    }

    private abstract static class MemoryRepository<T> {
        final List<T> entries = new ArrayList<>();

        void addEntry(T entry) {
            entries.add(entry);
        }

        boolean deleteEntry(java.util.function.Predicate<T> predicate) {
            return entries.removeIf(predicate);
        }
    }

    private static final class MemoryExpenseRepository
            extends MemoryRepository<Expense> implements ExpenseRepository {
        @Override public List<Expense> findAll() { return List.copyOf(entries); }
        @Override public Optional<Expense> findById(String id) {
            return entries.stream().filter(e -> e.getId().equals(id)).findFirst();
        }
        @Override public void add(Expense entry) { addEntry(entry); }
        @Override public void update(Expense entry) { replaceExpense(entry); }
        @Override public boolean deleteById(String id) {
            return deleteEntry(entry -> entry.getId().equals(id));
        }
        private void replaceExpense(Expense replacement) {
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).getId().equals(replacement.getId())) {
                    entries.set(index, replacement);
                    return;
                }
            }
        }
    }

    private static final class MemoryIncomeRepository
            extends MemoryRepository<Income> implements IncomeRepository {
        @Override public List<Income> findAll() { return List.copyOf(entries); }
        @Override public Optional<Income> findById(String id) {
            return entries.stream().filter(e -> e.getId().equals(id)).findFirst();
        }
        @Override public void add(Income entry) { addEntry(entry); }
        @Override public void update(Income entry) { replaceIncome(entry); }
        @Override public boolean deleteById(String id) {
            return deleteEntry(entry -> entry.getId().equals(id));
        }
        private void replaceIncome(Income replacement) {
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).getId().equals(replacement.getId())) {
                    entries.set(index, replacement);
                    return;
                }
            }
        }
    }

    private static final class MemoryTransferRepository
            extends MemoryRepository<Transfer> implements TransferRepository {
        @Override public List<Transfer> findAll() { return List.copyOf(entries); }
        @Override public Optional<Transfer> findById(String id) {
            return entries.stream().filter(e -> e.getId().equals(id)).findFirst();
        }
        @Override public void add(Transfer entry) { addEntry(entry); }
        @Override public void update(Transfer entry) { replaceTransfer(entry); }
        @Override public boolean deleteById(String id) {
            return deleteEntry(entry -> entry.getId().equals(id));
        }
        private void replaceTransfer(Transfer replacement) {
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).getId().equals(replacement.getId())) {
                    entries.set(index, replacement);
                    return;
                }
            }
        }
    }
}
