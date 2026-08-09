package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.Transfer;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads and removes a small set of realistic-looking finance activity so a
 * fresh workspace has something to show.
 *
 * <p>Every seeded record uses a fixed, stable identifier under the
 * {@code _PRESENTATION} suffix, generated relative to today's date each time this
 * runs. Loading checks each identifier before writing, so running it twice
 * never duplicates a record; removing deletes only those exact identifiers, so
 * it can never touch anything the signed-in user entered. Presentation accounts use
 * ordinary display names and are only created when no account with that name
 * already exists, matching the account service's own duplicate-name rule.
 *
 * <p>The service uses the same local finance services as manually entered
 * activity and never performs a network request.
 */
public final class PresentationDataService {

    private static final String BANK_NAME = "City Bank";
    private static final String BKASH_NAME = "bKash";
    private static final String NAGAD_NAME = "Nagad";

    private final AccountService accountService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final Path manifestPath;

    public PresentationDataService(
            AccountService accountService,
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            Path manifestPath) {
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.manifestPath = Objects.requireNonNull(
                manifestPath, "Presentation manifest path is required.")
                .toAbsolutePath().normalize();
    }

    /**
     * Adds the presentation accounts, income, expenses, and transfers that are not
     * already present. Safe to call any number of times.
     */
    public PresentationDataResult load() {
        LocalDate today = LocalDate.now();
        ManifestState manifest = readManifest();
        int priorAccounts = manifest.accounts.size();
        Map<String, Account> accounts = ensureAccounts(manifest);
        int addedAccounts = manifest.accounts.size() - priorAccounts;
        int addedIncome = 0;
        int addedExpenses = 0;
        int addedTransfers = 0;
        for (IncomeSeed seed : INCOME) {
            if (incomeService.findById(seed.id()).isEmpty()) {
                manifest.income.add(seed.id());
                saveManifest(manifest);
                try {
                    incomeService.createIncomeWithId(seed.id(),
                            today.minusDays(seed.daysAgo()), seed.amount(),
                            seed.source(), accounts.get(seed.account()),
                            seed.note());
                } catch (RuntimeException failure) {
                    manifest.income.remove(seed.id());
                    saveManifest(manifest);
                    throw failure;
                }
                addedIncome++;
            }
        }
        for (ExpenseSeed seed : EXPENSES) {
            if (expenseService.findExpenseById(seed.id()).isEmpty()) {
                manifest.expenses.add(seed.id());
                saveManifest(manifest);
                try {
                    expenseService.createExpenseWithId(seed.id(), seed.note(),
                            seed.amount(), today.minusDays(seed.daysAgo()),
                            seed.category(), accounts.get(seed.account()),
                            seed.note());
                } catch (RuntimeException failure) {
                    manifest.expenses.remove(seed.id());
                    saveManifest(manifest);
                    throw failure;
                }
                addedExpenses++;
            }
        }
        for (TransferSeed seed : TRANSFERS) {
            if (transferService.findById(seed.id()).isEmpty()) {
                manifest.transfers.add(seed.id());
                saveManifest(manifest);
                try {
                    transferService.createTransferWithId(seed.id(),
                            today.minusDays(seed.daysAgo()), seed.amount(),
                            accounts.get(seed.from()), accounts.get(seed.to()),
                            seed.note());
                } catch (RuntimeException failure) {
                    manifest.transfers.remove(seed.id());
                    saveManifest(manifest);
                    throw failure;
                }
                addedTransfers++;
            }
        }
        return new PresentationDataResult(
                addedAccounts, addedIncome, addedExpenses, addedTransfers);
    }

    /**
     * Removes exactly the records this service seeds. Presentation accounts are
     * archived rather than deleted, because no account in Wealthora can be
     * permanently deleted — archiving is the same action the Accounts screen
     * uses, and it leaves the account's history intact if it is ever needed.
     */
    public PresentationDataResult remove() {
        ManifestState manifest = readManifest();
        int removedTransfers = 0;
        for (String identifier : Set.copyOf(manifest.transfers)) {
            if (transferService.deleteTransfer(identifier)) {
                removedTransfers++;
            }
            manifest.transfers.remove(identifier);
            saveManifest(manifest);
        }
        int removedExpenses = 0;
        for (String identifier : Set.copyOf(manifest.expenses)) {
            if (expenseService.deleteExpense(identifier)) {
                removedExpenses++;
            }
            manifest.expenses.remove(identifier);
            saveManifest(manifest);
        }
        int removedIncome = 0;
        for (String identifier : Set.copyOf(manifest.income)) {
            if (incomeService.deleteIncome(identifier)) {
                removedIncome++;
            }
            manifest.income.remove(identifier);
            saveManifest(manifest);
        }
        int archivedAccounts = 0;
        for (Map.Entry<String, String> entry
                : Map.copyOf(manifest.accounts).entrySet()) {
            Account account = accountService.listAllAccounts().stream()
                    .filter(candidate -> candidate.getIdentifier()
                            .equals(entry.getValue()))
                    .findFirst().orElse(null);
            String expectedName = accountName(entry.getKey());
            if (account != null && account.isActive()
                    && account.getDisplayName().equals(expectedName)) {
                accountService.archiveAccount(account.getIdentifier());
                archivedAccounts++;
            }
            manifest.accounts.remove(entry.getKey());
            saveManifest(manifest);
        }
        return new PresentationDataResult(
                archivedAccounts, removedIncome, removedExpenses,
                removedTransfers);
    }

    /** True once every seeded record for this workspace already exists. */
    public boolean isLoaded() {
        ManifestState manifest = readManifest();
        return !manifest.isEmpty()
                && manifest.income.stream().allMatch(identifier ->
                        incomeService.findById(identifier).isPresent())
                && manifest.expenses.stream().allMatch(identifier ->
                        expenseService.findExpenseById(identifier).isPresent());
    }

    // --------------------------------------------------------------- accounts

    private Map<String, Account> ensureAccounts(ManifestState manifest) {
        LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
        accounts.put("CASH", Account.DEFAULT);
        accounts.put("BANK", ensureAccount(
                BANK_NAME, AccountType.BANK, new BigDecimal("15000.00"),
                "bank", "#1565C0", "BANK", manifest));
        accounts.put("BKASH", ensureAccount(
                BKASH_NAME, AccountType.MOBILE_BANKING,
                new BigDecimal("2500.00"), "mobile", "#E2136E", "BKASH",
                manifest));
        accounts.put("NAGAD", ensureAccount(
                NAGAD_NAME, AccountType.MOBILE_BANKING,
                new BigDecimal("1200.00"), "mobile", "#F7941D", "NAGAD",
                manifest));
        return accounts;
    }

    private Account ensureAccount(
            String name, AccountType type, BigDecimal openingBalance,
            String icon, String color, String key, ManifestState manifest) {
        Account existing = findByName(name);
        if (existing != null) {
            if (!existing.isActive()) {
                throw new ValidationException(
                        "Restore the archived " + name
                                + " account before loading presentation data.");
            }
            return existing;
        }
        String identifier = "ACCOUNT_PRESENTATION_" + key;
        manifest.accounts.put(key, identifier);
        saveManifest(manifest);
        try {
            return accountService.addAccountWithId(
                    identifier, name, type, openingBalance, icon, color);
        } catch (RuntimeException failure) {
            manifest.accounts.remove(key);
            saveManifest(manifest);
            throw failure;
        }
    }

    private Account findByName(String name) {
        return accountService.listAllAccounts().stream()
                .filter(account -> account.getDisplayName()
                        .equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private ManifestState readManifest() {
        ManifestState state = new ManifestState();
        if (Files.notExists(manifestPath)) {
            return state;
        }
        try {
            List<String> lines = Files.readAllLines(
                    manifestPath, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.getFirst().equals("format=1")) {
                throw new RepositoryException(
                        "The presentation-data manifest format is unsupported.");
            }
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw invalidManifest();
                }
                String name = line.substring(0, separator);
                String value = line.substring(separator + 1);
                if (name.startsWith("account.")) {
                    String key = name.substring("account.".length());
                    String expected = "ACCOUNT_PRESENTATION_" + key;
                    if (!List.of("BANK", "BKASH", "NAGAD").contains(key)
                            || !value.equals(expected)) {
                        throw invalidManifest();
                    }
                    state.accounts.put(key, value);
                } else if (name.equals("income")) {
                    state.income.addAll(parseIdentifiers(value,
                            INCOME.stream().map(IncomeSeed::id).toList()));
                } else if (name.equals("expenses")) {
                    state.expenses.addAll(parseIdentifiers(value,
                            EXPENSES.stream().map(ExpenseSeed::id).toList()));
                } else if (name.equals("transfers")) {
                    state.transfers.addAll(parseIdentifiers(value,
                            TRANSFERS.stream().map(TransferSeed::id).toList()));
                } else {
                    throw invalidManifest();
                }
            }
            return state;
        } catch (IOException exception) {
            throw new RepositoryException(
                    "The presentation-data manifest could not be read.",
                    exception);
        }
    }

    private void saveManifest(ManifestState state) {
        if (state.isEmpty()) {
            try {
                Files.deleteIfExists(manifestPath);
                return;
            } catch (IOException | SecurityException exception) {
                throw new RepositoryException(
                        "The presentation-data manifest could not be removed.",
                        exception);
            }
        }
        StringBuilder content = new StringBuilder("format=1\n");
        state.accounts.forEach((key, identifier) -> content.append("account.")
                .append(key).append('=').append(identifier).append('\n'));
        appendIdentifiers(content, "income", state.income);
        appendIdentifiers(content, "expenses", state.expenses);
        appendIdentifiers(content, "transfers", state.transfers);
        SafeFileSupport.write(manifestPath,
                content.toString().getBytes(StandardCharsets.UTF_8), true,
                ".wealthora-presentation-", "presentation-data manifest");
    }

    private static Set<String> parseIdentifiers(
            String value, List<String> allowed) {
        LinkedHashSet<String> identifiers = new LinkedHashSet<>();
        if (value.isBlank()) {
            return identifiers;
        }
        for (String identifier : value.split(",", -1)) {
            if (!allowed.contains(identifier) || !identifiers.add(identifier)) {
                throw invalidManifest();
            }
        }
        return identifiers;
    }

    private static void appendIdentifiers(
            StringBuilder content, String name, Set<String> identifiers) {
        content.append(name).append('=')
                .append(String.join(",", identifiers)).append('\n');
    }

    private static String accountName(String key) {
        return switch (key) {
            case "BANK" -> BANK_NAME;
            case "BKASH" -> BKASH_NAME;
            case "NAGAD" -> NAGAD_NAME;
            default -> throw invalidManifest();
        };
    }

    private static RepositoryException invalidManifest() {
        return new RepositoryException(
                "The presentation-data manifest is invalid.");
    }

    private static final class ManifestState {
        private final Map<String, String> accounts = new LinkedHashMap<>();
        private final Set<String> income = new LinkedHashSet<>();
        private final Set<String> expenses = new LinkedHashSet<>();
        private final Set<String> transfers = new LinkedHashSet<>();

        boolean isEmpty() {
            return accounts.isEmpty() && income.isEmpty()
                    && expenses.isEmpty() && transfers.isEmpty();
        }
    }

    // ------------------------------------------------------------------ data

    private record IncomeSeed(
            String id, int daysAgo, BigDecimal amount, String source,
            String account, String note) {
    }

    private record ExpenseSeed(
            String id, int daysAgo, BigDecimal amount, Category category,
            String account, String note) {
    }

    private record TransferSeed(
            String id, int daysAgo, BigDecimal amount, String from,
            String to, String note) {
    }

    private static final List<IncomeSeed> INCOME = List.of(
            new IncomeSeed("INCOME_PRESENTATION01", 88, new BigDecimal("25000.00"),
                    "Tuition received", "BANK", "Tuition received"),
            new IncomeSeed("INCOME_PRESENTATION02", 82, new BigDecimal("8000.00"),
                    "Part-time salary", "BANK", "Part-time salary"),
            new IncomeSeed("INCOME_PRESENTATION03", 68, new BigDecimal("5000.00"),
                    "Family support", "BKASH", "Family support"),
            new IncomeSeed("INCOME_PRESENTATION04", 54, new BigDecimal("8200.00"),
                    "Part-time salary", "BANK", "Part-time salary"),
            new IncomeSeed("INCOME_PRESENTATION05", 46, new BigDecimal("6500.00"),
                    "Freelance payment", "BKASH", "Freelance payment"),
            new IncomeSeed("INCOME_PRESENTATION06", 39, new BigDecimal("5000.00"),
                    "Family support", "BKASH", "Family support"),
            new IncomeSeed("INCOME_PRESENTATION07", 26, new BigDecimal("8100.00"),
                    "Part-time salary", "BANK", "Part-time salary"),
            new IncomeSeed("INCOME_PRESENTATION08", 11, new BigDecimal("5000.00"),
                    "Family support", "BKASH", "Family support"),
            new IncomeSeed("INCOME_PRESENTATION09", 4, new BigDecimal("3200.00"),
                    "Freelance payment", "NAGAD", "Freelance payment"));

    private static final List<ExpenseSeed> EXPENSES = List.of(
            new ExpenseSeed("EXPENSE_PRESENTATION01", 85, new BigDecimal("180.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION02", 84, new BigDecimal("1650.00"),
                    Category.FOOD, "BKASH", "Groceries"),
            new ExpenseSeed("EXPENSE_PRESENTATION03", 83, new BigDecimal("90.00"),
                    Category.TRANSPORT, "CASH", "Ride to campus"),
            new ExpenseSeed("EXPENSE_PRESENTATION04", 80, new BigDecimal("1200.00"),
                    Category.BILLS, "BANK", "Internet bill"),
            new ExpenseSeed("EXPENSE_PRESENTATION05", 78, new BigDecimal("150.00"),
                    Category.FOOD, "NAGAD", "Mobile recharge"),
            new ExpenseSeed("EXPENSE_PRESENTATION06", 76, new BigDecimal("220.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION07", 74, new BigDecimal("140.00"),
                    Category.FOOD, "CASH", "Coffee"),
            new ExpenseSeed("EXPENSE_PRESENTATION08", 71, new BigDecimal("650.00"),
                    Category.EDUCATION, "BANK", "Books"),
            new ExpenseSeed("EXPENSE_PRESENTATION09", 69, new BigDecimal("110.00"),
                    Category.TRANSPORT, "CASH", "Ride to campus"),
            new ExpenseSeed("EXPENSE_PRESENTATION10", 66, new BigDecimal("1800.00"),
                    Category.FOOD, "BKASH", "Groceries"),
            new ExpenseSeed("EXPENSE_PRESENTATION11", 64, new BigDecimal("400.00"),
                    Category.ENTERTAINMENT, "NAGAD", "Movie night"),
            new ExpenseSeed("EXPENSE_PRESENTATION12", 61, new BigDecimal("170.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION13", 59, new BigDecimal("95.00"),
                    Category.TRANSPORT, "CASH", "Ride to campus"),
            new ExpenseSeed("EXPENSE_PRESENTATION14", 56, new BigDecimal("130.00"),
                    Category.FOOD, "CASH", "Coffee"),
            new ExpenseSeed("EXPENSE_PRESENTATION15", 53, new BigDecimal("1200.00"),
                    Category.BILLS, "BANK", "Internet bill"),
            new ExpenseSeed("EXPENSE_PRESENTATION16", 51, new BigDecimal("200.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION17", 48, new BigDecimal("1700.00"),
                    Category.FOOD, "BKASH", "Groceries"),
            new ExpenseSeed("EXPENSE_PRESENTATION18", 45, new BigDecimal("160.00"),
                    Category.FOOD, "NAGAD", "Mobile recharge"),
            new ExpenseSeed("EXPENSE_PRESENTATION19", 43, new BigDecimal("85.00"),
                    Category.TRANSPORT, "CASH", "Ride to campus"),
            new ExpenseSeed("EXPENSE_PRESENTATION20", 40, new BigDecimal("190.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION21", 37, new BigDecimal("550.00"),
                    Category.EDUCATION, "BANK", "Course materials"),
            new ExpenseSeed("EXPENSE_PRESENTATION22", 34, new BigDecimal("135.00"),
                    Category.FOOD, "CASH", "Coffee"),
            new ExpenseSeed("EXPENSE_PRESENTATION23", 31, new BigDecimal("1900.00"),
                    Category.FOOD, "BKASH", "Groceries"),
            new ExpenseSeed("EXPENSE_PRESENTATION24", 28, new BigDecimal("100.00"),
                    Category.TRANSPORT, "CASH", "Ride to campus"),
            new ExpenseSeed("EXPENSE_PRESENTATION25", 25, new BigDecimal("350.00"),
                    Category.ENTERTAINMENT, "NAGAD", "Streaming subscription"),
            new ExpenseSeed("EXPENSE_PRESENTATION26", 22, new BigDecimal("175.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION27", 19, new BigDecimal("1200.00"),
                    Category.BILLS, "BANK", "Internet bill"),
            new ExpenseSeed("EXPENSE_PRESENTATION28", 16, new BigDecimal("145.00"),
                    Category.FOOD, "CASH", "Coffee"),
            new ExpenseSeed("EXPENSE_PRESENTATION29", 13, new BigDecimal("1650.00"),
                    Category.FOOD, "BKASH", "Groceries"),
            new ExpenseSeed("EXPENSE_PRESENTATION30", 10, new BigDecimal("155.00"),
                    Category.FOOD, "NAGAD", "Mobile recharge"),
            new ExpenseSeed("EXPENSE_PRESENTATION31", 7, new BigDecimal("95.00"),
                    Category.TRANSPORT, "CASH", "Ride to campus"),
            new ExpenseSeed("EXPENSE_PRESENTATION32", 5, new BigDecimal("210.00"),
                    Category.FOOD, "CASH", "Lunch"),
            new ExpenseSeed("EXPENSE_PRESENTATION33", 3, new BigDecimal("600.00"),
                    Category.EDUCATION, "BANK", "Books"),
            new ExpenseSeed("EXPENSE_PRESENTATION34", 1, new BigDecimal("150.00"),
                    Category.FOOD, "CASH", "Coffee"));

    private static final List<TransferSeed> TRANSFERS = List.of(
            new TransferSeed("TRANSFER_PRESENTATION01", 87, new BigDecimal("5000.00"),
                    "BANK", "BKASH", "Mobile wallet top-up"),
            new TransferSeed("TRANSFER_PRESENTATION02", 60, new BigDecimal("2000.00"),
                    "BANK", "NAGAD", "Mobile wallet top-up"),
            new TransferSeed("TRANSFER_PRESENTATION03", 20, new BigDecimal("3000.00"),
                    "BANK", "BKASH", "Mobile wallet top-up"));

    /** Counts of records this call actually added or removed, by section. */
    public record PresentationDataResult(
            int accounts, int income, int expenses, int transfers) {

        public int total() {
            return accounts + income + expenses + transfers;
        }
    }
}
