package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FinanceRepositoryTest {

    private static final Account BANK = Account.createCustom(
            "ACCOUNT_BANK",
            "Savings",
            AccountType.BANK,
            new BigDecimal("100.00"),
            false);
    private static int passed;

    private FinanceRepositoryTest() {
    }

    public static void main(String[] args) {
        test("missing files are read-only", FinanceRepositoryTest::missingFiles);
        test("zero-byte stores are empty", FinanceRepositoryTest::zeroByteFiles);
        test("account round trip", FinanceRepositoryTest::accountRoundTrip);
        test("account duplicate preservation", FinanceRepositoryTest::accountDuplicate);
        test("account default name reservation",
                FinanceRepositoryTest::accountDefaultName);
        test("account corruption rejection", FinanceRepositoryTest::accountCorruption);
        test("income escaped round trip", FinanceRepositoryTest::incomeRoundTrip);
        test("income update preserves position", FinanceRepositoryTest::incomeUpdate);
        test("income delete behavior", FinanceRepositoryTest::incomeDelete);
        test("income duplicate preservation", FinanceRepositoryTest::incomeDuplicate);
        test("income corruption rejection", FinanceRepositoryTest::incomeCorruption);
        test("transfer round trip", FinanceRepositoryTest::transferRoundTrip);
        test("transfer update and delete", FinanceRepositoryTest::transferUpdateDelete);
        test("transfer corruption rejection", FinanceRepositoryTest::transferCorruption);
        test("legacy expenses resolve default", FinanceRepositoryTest::legacyExpense);
        test("account-aware expenses round trip", FinanceRepositoryTest::accountExpense);
        test("unknown expense account is rejected", FinanceRepositoryTest::unknownExpenseAccount);
        test("reads preserve original bytes", FinanceRepositoryTest::readPreservesBytes);
        test("successful writes clean temporary files", FinanceRepositoryTest::temporaryCleanup);
        System.out.println(
                "All " + passed + " finance repository tests passed.");
    }

    private static void missingFiles() throws IOException {
        withDirectory(directory -> {
            Path accounts = directory.resolve("accounts.csv");
            Path income = directory.resolve("income.csv");
            Path transfers = directory.resolve("transfers.csv");
            assertTrue(new CsvAccountRepository(accounts).findAll().isEmpty());
            assertTrue(new CsvIncomeRepository(
                    income, FinanceRepositoryTest::resolveAccount)
                    .findAll().isEmpty());
            assertTrue(new CsvTransferRepository(
                    transfers, FinanceRepositoryTest::resolveAccount)
                    .findAll().isEmpty());
            assertFalse(Files.exists(accounts));
            assertFalse(Files.exists(income));
            assertFalse(Files.exists(transfers));
        });
    }

    private static void zeroByteFiles() throws IOException {
        withDirectory(directory -> {
            Path accounts = Files.createFile(directory.resolve("accounts.csv"));
            Path income = Files.createFile(directory.resolve("income.csv"));
            Path transfers = Files.createFile(directory.resolve("transfers.csv"));
            assertTrue(new CsvAccountRepository(accounts).findAll().isEmpty());
            assertTrue(new CsvIncomeRepository(
                    income, FinanceRepositoryTest::resolveAccount)
                    .findAll().isEmpty());
            assertTrue(new CsvTransferRepository(
                    transfers, FinanceRepositoryTest::resolveAccount)
                    .findAll().isEmpty());
        });
    }

    private static void accountRoundTrip() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            CsvAccountRepository repository = new CsvAccountRepository(path);
            repository.add(BANK);
            Account loaded = repository.findAll().get(0);
            assertEquals(BANK.getIdentifier(), loaded.getIdentifier());
            assertEquals(BANK.getDisplayName(), loaded.getDisplayName());
            assertMoney("100.00", loaded.getOpeningBalance());
            assertTrue(Files.readString(path).startsWith(
                    CsvAccountRepository.HEADER + "\n"));
        });
    }

    private static void accountDuplicate() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            CsvAccountRepository repository = new CsvAccountRepository(path);
            repository.add(BANK);
            byte[] before = Files.readAllBytes(path);
            expect(RepositoryException.class, () -> repository.add(BANK));
            assertArrayEquals(before, Files.readAllBytes(path));
        });
    }

    private static void accountDefaultName() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            CsvAccountRepository repository = new CsvAccountRepository(path);
            Account conflicting = Account.createCustom(
                    "ACCOUNT_CASH_ALIAS",
                    "cash",
                    AccountType.CASH,
                    BigDecimal.ZERO,
                    false);
            expect(RepositoryException.class, () -> repository.add(conflicting));
            assertFalse(Files.exists(path));
        });
    }

    private static void accountCorruption() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            Files.writeString(
                    path,
                    CsvAccountRepository.HEADER
                    + "\nACCOUNT_BAD,Cash,CASH,0.00,ACTIVE\n",
                    StandardCharsets.UTF_8);
            expect(
                    RepositoryException.class,
                    () -> new CsvAccountRepository(path).findAll());
        });
    }

    private static void incomeRoundTrip() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("income.csv");
            CsvIncomeRepository repository = new CsvIncomeRepository(
                    path, FinanceRepositoryTest::resolveAccount);
            Income income = new Income(
                    "INCOME_FIXED",
                    LocalDate.of(2026, 7, 1),
                    new BigDecimal("1234.50"),
                    "Consulting, \"International\"",
                    BANK,
                    "Paid in two parts\nধন্যবাদ");
            repository.add(income);
            Income loaded = repository.findAll().get(0);
            assertEquals(income.getId(), loaded.getId());
            assertEquals(income.getSource(), loaded.getSource());
            assertEquals(income.getNote(), loaded.getNote());
            assertEquals(BANK, loaded.getAccount());
            assertMoney("1234.50", loaded.getAmount());
        });
    }

    private static void incomeUpdate() throws IOException {
        withDirectory(directory -> {
            CsvIncomeRepository repository = incomeRepository(directory);
            Income first = income("INCOME_FIRST", "First", Account.DEFAULT);
            Income second = income("INCOME_SECOND", "Second", BANK);
            repository.add(first);
            repository.add(second);
            repository.update(new Income(
                    first.getId(),
                    first.getDate(),
                    new BigDecimal("99.00"),
                    "Updated",
                    BANK,
                    ""));
            List<Income> loaded = repository.findAll();
            assertEquals(first.getId(), loaded.get(0).getId());
            assertEquals(second.getId(), loaded.get(1).getId());
            assertEquals("Updated", loaded.get(0).getSource());
        });
    }

    private static void incomeDelete() throws IOException {
        withDirectory(directory -> {
            CsvIncomeRepository repository = incomeRepository(directory);
            Income first = income("INCOME_FIRST", "First", Account.DEFAULT);
            Income second = income("INCOME_SECOND", "Second", BANK);
            repository.add(first);
            repository.add(second);
            assertTrue(repository.deleteById(first.getId()));
            assertFalse(repository.deleteById("INCOME_MISSING"));
            assertEquals(List.of(second), repository.findAll());
        });
    }

    private static void incomeDuplicate() throws IOException {
        withDirectory(directory -> {
            CsvIncomeRepository repository = incomeRepository(directory);
            Income income = income("INCOME_FIXED", "Salary", BANK);
            repository.add(income);
            Path path = directory.resolve("income.csv");
            byte[] before = Files.readAllBytes(path);
            expect(RepositoryException.class, () -> repository.add(income));
            assertArrayEquals(before, Files.readAllBytes(path));
        });
    }

    private static void incomeCorruption() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("income.csv");
            Files.writeString(
                    path,
                    CsvIncomeRepository.LEGACY_HEADER
                    + "\nINCOME_X,2026-07-01,10.00,Salary,ACCOUNT_UNKNOWN,\n",
                    StandardCharsets.UTF_8);
            byte[] before = Files.readAllBytes(path);
            expect(
                    RepositoryException.class,
                    () -> new CsvIncomeRepository(
                            path, FinanceRepositoryTest::resolveAccount)
                            .findAll());
            assertArrayEquals(before, Files.readAllBytes(path));
        });
    }

    private static void transferRoundTrip() throws IOException {
        withDirectory(directory -> {
            CsvTransferRepository repository = transferRepository(directory);
            Transfer transfer = transfer("TRANSFER_FIXED", "Move,\n\"now\"");
            repository.add(transfer);
            Transfer loaded = repository.findAll().get(0);
            assertEquals(transfer.getId(), loaded.getId());
            assertEquals(transfer.getNote(), loaded.getNote());
            assertEquals(Account.DEFAULT, loaded.getSourceAccount());
            assertEquals(BANK, loaded.getDestinationAccount());
            assertMoney("25.00", loaded.getAmount());
        });
    }

    private static void transferUpdateDelete() throws IOException {
        withDirectory(directory -> {
            CsvTransferRepository repository = transferRepository(directory);
            Transfer first = transfer("TRANSFER_FIRST", "First");
            Transfer second = transfer("TRANSFER_SECOND", "Second");
            repository.add(first);
            repository.add(second);
            repository.update(new Transfer(
                    first.getId(),
                    first.getDate(),
                    new BigDecimal("30.00"),
                    BANK,
                    Account.DEFAULT,
                    "Updated"));
            assertEquals(first.getId(), repository.findAll().get(0).getId());
            assertTrue(repository.deleteById(second.getId()));
            assertFalse(repository.deleteById("TRANSFER_MISSING"));
            assertEquals(1, repository.findAll().size());
        });
    }

    private static void transferCorruption() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("transfers.csv");
            Files.writeString(
                    path,
                    CsvTransferRepository.LEGACY_HEADER
                    + "\nTRANSFER_X,2026-07-01,10.00,"
                    + "ACCOUNT_DEFAULT_CASH,ACCOUNT_DEFAULT_CASH,\n",
                    StandardCharsets.UTF_8);
            expect(
                    RepositoryException.class,
                    () -> new CsvTransferRepository(
                            path, FinanceRepositoryTest::resolveAccount)
                            .findAll());
        });
    }

    private static void legacyExpense() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("expenses.csv");
            String legacy = CsvExpenseCodec.HEADER
                    + "\nlegacy-id,Lunch,12.50,2026-07-01,FOOD,note\n";
            Files.writeString(path, legacy, StandardCharsets.UTF_8);
            CsvExpenseRepository repository = new CsvExpenseRepository(path);
            Expense loaded = repository.findAll().get(0);
            assertEquals(Account.DEFAULT, loaded.getAccount());
            assertEquals(legacy, Files.readString(path));
        });
    }

    private static void accountExpense() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("expenses.csv");
            CsvExpenseRepository repository = new CsvExpenseRepository(
                    path, Category::valueOf, FinanceRepositoryTest::resolveAccount);
            Expense expense = new Expense(
                    "expense-account",
                    "Groceries",
                    new BigDecimal("42.75"),
                    LocalDate.of(2026, 7, 1),
                    Category.FOOD,
                    BANK,
                    "Weekly");
            repository.add(expense);
            assertTrue(Files.readString(path).startsWith(
                    CsvExpenseCodec.V2_HEADER + "\n"));
            Expense loaded = repository.findAll().get(0);
            assertEquals(BANK, loaded.getAccount());
            assertEquals(expense.getDescription(), loaded.getDescription());
        });
    }

    private static void unknownExpenseAccount() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("expenses.csv");
            Files.writeString(
                    path,
                    CsvExpenseCodec.ACCOUNT_HEADER
                    + "\nexpense-x,Lunch,10.00,2026-07-01,FOOD,"
                    + "ACCOUNT_UNKNOWN,\n",
                    StandardCharsets.UTF_8);
            expect(RepositoryException.class, () -> new CsvExpenseRepository(
                    path, Category::valueOf, FinanceRepositoryTest::resolveAccount)
                    .findAll());
        });
    }

    private static void readPreservesBytes() throws IOException {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            String text = CsvAccountRepository.LEGACY_HEADER + "\r\n"
                    + "ACCOUNT_BANK,Savings,BANK,100.00,ACTIVE\r\n";
            Files.writeString(path, text, StandardCharsets.UTF_8);
            byte[] before = Files.readAllBytes(path);
            new CsvAccountRepository(path).findAll();
            assertArrayEquals(before, Files.readAllBytes(path));
        });
    }

    private static void temporaryCleanup() throws IOException {
        withDirectory(directory -> {
            incomeRepository(directory).add(
                    income("INCOME_FIXED", "Salary", Account.DEFAULT));
            transferRepository(directory).add(
                    transfer("TRANSFER_FIXED", ""));
            new CsvAccountRepository(directory.resolve("accounts.csv")).add(BANK);
            try (var files = Files.list(directory)) {
                assertTrue(files.noneMatch(path ->
                    path.getFileName().toString().endsWith(".tmp")));
            }
        });
    }

    private static CsvIncomeRepository incomeRepository(Path directory) {
        return new CsvIncomeRepository(
                directory.resolve("income.csv"),
                FinanceRepositoryTest::resolveAccount);
    }

    private static CsvTransferRepository transferRepository(Path directory) {
        return new CsvTransferRepository(
                directory.resolve("transfers.csv"),
                FinanceRepositoryTest::resolveAccount);
    }

    private static Account resolveAccount(String identifier) {
        if (Account.DEFAULT_IDENTIFIER.equals(identifier)) {
            return Account.DEFAULT;
        }
        if (BANK.getIdentifier().equals(identifier)) {
            return BANK;
        }
        throw new IllegalArgumentException(
                "Unknown account: " + identifier);
    }

    private static Income income(
            String id, String source, Account account) {
        return new Income(
                id,
                LocalDate.of(2026, 7, 1),
                new BigDecimal("50.00"),
                source,
                account,
                "");
    }

    private static Transfer transfer(String id, String note) {
        return new Transfer(
                id,
                LocalDate.of(2026, 7, 2),
                new BigDecimal("25.00"),
                Account.DEFAULT,
                BANK,
                note);
    }

    private static void withDirectory(ThrowingConsumer<Path> action)
            throws IOException {
        Path directory = Files.createTempDirectory("spendwise-finance-test-");
        try {
            action.accept(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(FinanceRepositoryTest::delete);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new AssertionError(
                    "Could not clean test-owned path: " + path, exception);
        }
    }

    private static void test(String name, ThrowingRunnable test) {
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

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError("File bytes changed.");
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
        throw new AssertionError(
                "Expected " + expected.getSimpleName() + ".");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {

        void accept(T value) throws IOException;
    }
}
