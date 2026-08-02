package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.CardType;
import com.spendwise.model.Category;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvCurrencyPreferenceRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvPaymentCardRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class Phase2FinanceArchitectureTest {

    private int passed;

    public static void main(String[] args) throws Exception {
        new Phase2FinanceArchitectureTest().run();
    }

    private void run() throws Exception {
        test("transaction types", this::transactionTypesAreExact);
        test("balance lifecycle", this::balanceLifecycleIsConsistent);
        test("legacy expense migration", this::legacyExpenseMigratesOnWrite);
        test("legacy account migration", this::legacyAccountMigratesOnWrite);
        test("metadata account migration",
                this::metadataAccountMigratesOnWrite);
        test("subcategory migration", this::subcategoryMigratesSafely);
        test("card safety", this::cardProfilesRejectSensitiveNumbers);
        test("currency persistence", this::currencyIsConfigurable);
        System.out.println("All " + passed
                + " Phase 2 finance architecture tests passed.");
    }

    private void transactionTypesAreExact() {
        assertEquals(List.of("INCOME", "EXPENSE", "TRANSFER"),
                java.util.Arrays.stream(TransactionType.values())
                        .map(Enum::name).toList());
        assertEquals(List.of(
                "CASH", "BANK", "SAVINGS", "MOBILE_BANKING",
                "DIGITAL_WALLET", "CREDIT_CARD", "DEBIT_CARD", "OTHER"),
                java.util.Arrays.stream(AccountType.values())
                        .map(Enum::name).toList());
    }

    private void balanceLifecycleIsConsistent() throws Exception {
        withDirectory(directory -> {
            AccountService accounts = accountService(directory);
            Account bank = accounts.addAccount("Bank", AccountType.BANK,
                    money("100"));
            Account savings = accounts.addAccount("Savings", AccountType.SAVINGS,
                    money("50"));
            ExpenseService expenses = expenseService(directory, accounts);
            IncomeService income = incomeService(directory, accounts);
            TransferService transfers = transferService(directory, accounts);
            FinanceService finance = new FinanceService(
                    accounts, expenses, income, transfers);

            expectThrows(ValidationException.class, () ->
                    transfers.createTransfer(LocalDate.now(), money("-1"),
                            bank, savings, List.of(), "Invalid"));
            expectThrows(ValidationException.class, () ->
                    transfers.createTransfer(LocalDate.now(), money("1"),
                            bank, bank, List.of(), "Invalid"));

            var salary = income.createIncome(LocalDate.now(), money("30"),
                    "Salary", bank, PaymentMethod.BANK_TRANSFER,
                    List.of("work"), "Monthly salary");
            var food = expenses.createExpense("Lunch", money("20"),
                    LocalDate.now(), Category.FOOD, bank, PaymentMethod.DEBIT_CARD,
                    List.of("meal"), "");
            var transfer = transfers.createTransfer(LocalDate.now(), money("25"),
                    bank, savings, List.of("reserve"), "Move to savings");
            assertBalances(finance, bank, "85.00", savings, "75.00", "160.00");

            transfers.updateTransfer(transfer.getId(), LocalDate.now(), money("40"),
                    bank, savings, transfer.getTags(), transfer.getNote());
            assertBalances(finance, bank, "70.00", savings, "90.00", "160.00");
            assertTrue(transfers.deleteTransfer(transfer.getId()));
            assertBalances(finance, bank, "110.00", savings, "50.00", "160.00");

            income.updateIncome(salary.getId(), LocalDate.now(), money("40"),
                    "Salary", bank, salary.getPaymentMethod(), salary.getTags(), "");
            assertBalances(finance, bank, "120.00", savings, "50.00", "170.00");
            assertTrue(income.deleteIncome(salary.getId()));
            assertBalances(finance, bank, "80.00", savings, "50.00", "130.00");
            assertTrue(expenses.deleteExpense(food.getId()));
            assertBalances(finance, bank, "100.00", savings, "50.00", "150.00");
        });
    }

    private void legacyExpenseMigratesOnWrite() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("expenses.csv");
            Files.writeString(path, "id,description,amount,date,category,notes\n"
                    + "legacy-id,Lunch,12.50,2025-01-02,FOOD,old data\n");
            AccountService accounts = accountService(directory);
            ExpenseService expenses = new ExpenseService(new CsvExpenseRepository(
                    path, Category::valueOf, accounts::resolveAccount), accounts);
            assertEquals(Account.DEFAULT, expenses.getAllExpenses().get(0).getAccount());
            expenses.createExpense("Bus", money("3.00"),
                    LocalDate.of(2025, 1, 3), Category.TRANSPORT, Account.DEFAULT,
                    PaymentMethod.CASH, List.of("commute"), "");
            String migrated = Files.readString(path);
            assertTrue(migrated.startsWith("id,description,amount,date,category,"
                    + "account,paymentMethod,tags,notes\n"));
            assertTrue(migrated.contains("legacy-id"));
            assertEquals(2, expenses.getAllExpenses().size());
        });
    }

    private void legacyAccountMigratesOnWrite() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            Files.writeString(path, "id,name,type,openingBalance,status\n"
                    + "ACCOUNT_LEGACY,Legacy Wallet,MOBILE_WALLET,10.00,ACTIVE\n");
            AccountService service = new AccountService(
                    new CsvAccountRepository(path));
            Account legacy = service.resolveAccount("ACCOUNT_LEGACY");
            assertEquals(AccountType.MOBILE_BANKING, legacy.getType());
            service.updateAccountDetails(legacy.getIdentifier(), "Mobile Wallet",
                    legacy.getType(), legacy.getOpeningBalance(), "mobile", "#336699");
            assertTrue(Files.readString(path).startsWith(
                    CsvAccountRepository.HEADER + "\n"));
            assertTrue(Files.exists(path.resolveSibling(
                    "accounts.csv.pre-metadata-backup")));
        });
    }

    private void metadataAccountMigratesOnWrite() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("accounts.csv");
            Files.writeString(path, CsvAccountRepository.LEGACY_METADATA_HEADER
                    + "\nACCOUNT_WALLET,bKash,MOBILE_BANKING,20.00,ACTIVE,"
                    + "mobile,#B33A62\n");
            AccountService service = new AccountService(
                    new CsvAccountRepository(path));
            Account wallet = service.resolveAccount("ACCOUNT_WALLET");
            assertEquals("BDT", wallet.getCurrencyCode());
            assertTrue(wallet.getCreatedDate().isEmpty());
            service.updateAccountDetails(wallet.getIdentifier(), "bKash",
                    wallet.getType(), wallet.getOpeningBalance(),
                    wallet.getIconName(), wallet.getColorHex(), "BDT", "bKash");
            assertTrue(Files.readString(path).startsWith(
                    CsvAccountRepository.HEADER + "\n"));
            assertTrue(Files.readString(path.resolveSibling(
                    "accounts.csv.pre-metadata-backup"))
                    .startsWith(CsvAccountRepository.LEGACY_METADATA_HEADER));
        });
    }

    private void subcategoryMigratesSafely() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("categories.csv");
            Files.writeString(path, "id,name,status\n"
                    + "CUSTOM_HOME,Home,ACTIVE\n");
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(path), () -> "CUSTOM_GROCERIES");
            Category child = categories.addSubcategory("Groceries", "CUSTOM_HOME");
            assertEquals("CUSTOM_HOME", child.getParentIdentifier().orElseThrow());
            assertTrue(Files.readString(path).startsWith(
                    "id,name,parent,status\n"));
        });
    }

    private void cardProfilesRejectSensitiveNumbers() throws Exception {
        withDirectory(directory -> {
            AccountService accounts = accountService(directory);
            Account cardAccount = accounts.addAccount("Credit Card",
                    AccountType.CREDIT_CARD, money("0"));
            Account payment = accounts.addAccount("Payment Bank",
                    AccountType.BANK, money("0"));
            PaymentCardService cards = new PaymentCardService(
                    new CsvPaymentCardRepository(directory.resolve("cards.csv"),
                            accounts::resolveAccount), accounts);
            var card = cards.addCard("University Card", "Example Bank",
                    CardType.CREDIT, "1234", money("50000"), 10, 25,
                    cardAccount, payment);
            assertEquals("1234", card.getLastFourDigits());
            expectThrows(ValidationException.class, () -> cards.addCard(
                    "Unsafe", "Example Bank", CardType.CREDIT,
                    "4111111111111111", money("50000"), 10, 25,
                    cardAccount, payment));
            assertTrue(!Files.readString(directory.resolve("cards.csv"))
                    .contains("4111111111111111"));
        });
    }

    private void currencyIsConfigurable() throws Exception {
        withDirectory(directory -> {
            CurrencyService first = new CurrencyService(
                    new CsvCurrencyPreferenceRepository(
                            directory.resolve("currency-settings.csv")));
            assertEquals("BDT", first.getCurrency().getCurrencyCode());
            first.setCurrency("USD");
            CurrencyService restarted = new CurrencyService(
                    new CsvCurrencyPreferenceRepository(
                            directory.resolve("currency-settings.csv")));
            assertEquals("USD", restarted.getCurrency().getCurrencyCode());
            assertEquals("USD 12.50", restarted.format(money("12.5")));
        });
    }

    private static AccountService accountService(Path directory) {
        return new AccountService(new CsvAccountRepository(
                directory.resolve("accounts.csv")));
    }

    private static ExpenseService expenseService(
            Path directory, AccountService accounts) {
        return new ExpenseService(new CsvExpenseRepository(
                directory.resolve("expenses.csv"), Category::valueOf,
                accounts::resolveAccount), accounts);
    }

    private static IncomeService incomeService(
            Path directory, AccountService accounts) {
        return new IncomeService(new CsvIncomeRepository(
                directory.resolve("income.csv"), accounts::resolveAccount), accounts);
    }

    private static TransferService transferService(
            Path directory, AccountService accounts) {
        return new TransferService(new CsvTransferRepository(
                directory.resolve("transfers.csv"), accounts::resolveAccount), accounts);
    }

    private static void assertBalances(
            FinanceService finance, Account first, String firstBalance,
            Account second, String secondBalance, String total) {
        AccountBalanceSnapshot snapshot = finance.calculateBalances();
        assertEquals(money(firstBalance), snapshot.getBalance(first));
        assertEquals(money(secondBalance), snapshot.getBalance(second));
        assertEquals(money(total), snapshot.getTotalBalance());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private void test(String name, ThrowingRunnable test) throws Exception {
        try {
            test.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError("Phase 2 test failed: " + name, failure);
        }
    }

    private static void withDirectory(DirectoryTest test) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-phase2-");
        try {
            test.run(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void expectThrows(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Unexpected exception type.", failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true.");
        }
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

    @FunctionalInterface
    private interface DirectoryTest {
        void run(Path directory) throws Exception;
    }
}
