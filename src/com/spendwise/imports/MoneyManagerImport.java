package com.spendwise.imports;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.Transfer;
import com.spendwise.service.FinanceWorkspace;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a Money Manager backup into a Wealthora workspace.
 *
 * <p>The mapping was derived by reading a real backup's schema rather than from
 * guesswork, and only fields that map cleanly are carried across. Records that
 * cannot be mapped are counted and explained in the returned warnings instead
 * of being dropped in silence.
 *
 * <p>Money Manager stores each transfer as two linked rows, an outgoing and an
 * incoming half sharing a transaction identifier. Those are re-joined into a
 * single Wealthora transfer so the amount is not counted twice.
 */
public final class MoneyManagerImport {

    private static final String TYPE_INCOME = "0";
    private static final String TYPE_EXPENSE = "1";
    private static final String TYPE_TRANSFER_OUT = "3";
    private static final String TYPE_TRANSFER_IN = "4";

    /** Money Manager's account-group codes, in its own numbering. */
    private static final Map<String, AccountType> GROUP_TYPES = Map.of(
            "1", AccountType.BANK,
            "2", AccountType.CREDIT_CARD,
            "3", AccountType.DEBIT_CARD,
            "4", AccountType.SAVINGS,
            "6", AccountType.DIGITAL_WALLET,
            "11", AccountType.CASH);

    private MoneyManagerImport() {
    }

    /**
     * Reads {@code backup} and writes the mapped data into a workspace rooted at
     * {@code stagingDirectory}. The source file is only ever read.
     */
    public static Result read(Path backup, Path stagingDirectory) {
        SqliteFile database = ForeignBackupDetector.openMoneyManagerDatabase(
                Objects.requireNonNull(backup, "Backup file is required."));
        FinanceWorkspace workspace = FinanceWorkspace.overDirectory(
                Objects.requireNonNull(
                        stagingDirectory, "Staging folder is required."));
        return new Mapper(database, workspace).run();
    }

    /** The mapped workspace plus everything the user should know about it. */
    public record Result(
            FinanceWorkspace workspace,
            List<String> warnings,
            int skippedRecords,
            String currencyCode) {

        public Result {
            Objects.requireNonNull(workspace, "Workspace is required.");
            warnings = List.copyOf(Objects.requireNonNull(
                    warnings, "Warnings are required."));
            currencyCode = currencyCode == null ? "" : currencyCode;
        }
    }

    private static final class Mapper {

        private final SqliteFile database;
        private final FinanceWorkspace workspace;
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Account> accountsByUid = new LinkedHashMap<>();
        private final Map<String, Category> categoriesByUid =
                new LinkedHashMap<>();
        private final Map<String, Category> categoriesByName =
                new LinkedHashMap<>();
        private int skipped;
        private int unmappedCategories;
        private String currencyCode = Account.DEFAULT_CURRENCY_CODE;

        private Mapper(SqliteFile database, FinanceWorkspace workspace) {
            this.database = database;
            this.workspace = workspace;
        }

        private Result run() {
            readCurrency();
            readAccounts();
            readCategories();
            readTransactions();
            if (unmappedCategories > 0) {
                warnings.add(unmappedCategories
                        + " transaction(s) had no matching category and were "
                        + "filed under Other.");
            }
            return new Result(workspace, warnings, skipped, currencyCode);
        }

        private void readCurrency() {
            for (Map<String, Object> row : rows("CURRENCY")) {
                String iso = text(row.get("ISO"));
                if (!iso.isEmpty() && iso.length() == 3) {
                    currencyCode = iso.toUpperCase(Locale.ROOT);
                    if ("1".equals(text(row.get("IS_MAIN_CURRENCY")))) {
                        return;
                    }
                }
            }
        }

        private void readAccounts() {
            Map<String, String> groupTypes = new LinkedHashMap<>();
            for (Map<String, Object> row : rows("ASSETGROUP")) {
                groupTypes.put(text(row.get("uid")), text(row.get("TYPE")));
            }
            Map<String, Account> byName = new LinkedHashMap<>();
            int index = 0;
            for (Map<String, Object> row : rows("ASSETS")) {
                String uid = text(row.get("uid"));
                String name = text(row.get("NIC_NAME"));
                if (uid.isEmpty() || name.isEmpty()) {
                    skipped++;
                    continue;
                }
                // Wealthora keeps account names unique and already owns a
                // built-in cash account, while Money Manager allows repeats.
                // Point the repeat at the account that already covers that
                // name instead of dropping it and orphaning its transactions.
                Account existing = key(name).equals(
                        key(Account.DEFAULT.getDisplayName()))
                        ? Account.DEFAULT
                        : byName.get(key(name));
                if (existing != null) {
                    accountsByUid.put(uid, existing);
                    warnings.add("Money Manager account \"" + name
                            + "\" was merged into the existing Wealthora "
                            + "account of the same name.");
                    continue;
                }
                AccountType type = GROUP_TYPES.getOrDefault(
                        groupTypes.getOrDefault(text(row.get("groupUid")), ""),
                        AccountType.OTHER);
                String identifier = "ACCOUNT_MM" + (++index);
                try {
                    Account account = Account.createCustom(identifier, name,
                            type, BigDecimal.ZERO.setScale(2),
                            iconFor(type), Account.DEFAULT_COLOR,
                            currencyCode, "", LocalDate.now(), false);
                    workspace.accounts().add(account);
                    accountsByUid.put(uid, account);
                    byName.put(key(name), account);
                } catch (RuntimeException failure) {
                    skipped++;
                    warnings.add("Account \"" + name
                            + "\" could not be imported: " + reason(failure));
                }
            }
        }

        private void readCategories() {
            int index = 0;
            for (Map<String, Object> row : rows("ZCATEGORY")) {
                String uid = text(row.get("uid"));
                String name = text(row.get("NAME"));
                if (uid.isEmpty() || name.isEmpty()
                        || "1".equals(text(row.get("C_IS_DEL")))) {
                    continue;
                }
                Category builtIn = builtInFor(name);
                if (builtIn != null) {
                    categoriesByUid.put(uid, builtIn);
                    categoriesByName.putIfAbsent(key(name), builtIn);
                    continue;
                }
                Category existing = categoriesByName.get(key(name));
                if (existing != null) {
                    categoriesByUid.put(uid, existing);
                    continue;
                }
                String identifier = "CUSTOM_MM" + (++index);
                try {
                    Category category = Category.createCustom(
                            identifier, name, false);
                    workspace.categories().add(category);
                    categoriesByUid.put(uid, category);
                    categoriesByName.put(key(name), category);
                } catch (RuntimeException failure) {
                    warnings.add("Category \"" + name
                            + "\" could not be imported: " + reason(failure));
                }
            }
        }

        private void readTransactions() {
            List<Map<String, Object>> transactions = rows("INOUTCOME");
            Map<String, Map<String, Object>> transferOut = new LinkedHashMap<>();
            int index = 0;
            for (Map<String, Object> row : transactions) {
                if ("1".equals(text(row.get("IS_DEL")))) {
                    continue;
                }
                String type = text(row.get("DO_TYPE"));
                index++;
                switch (type) {
                    case TYPE_INCOME -> addIncome(row, index);
                    case TYPE_EXPENSE -> addExpense(row, index);
                    case TYPE_TRANSFER_OUT -> {
                        String link = text(row.get("txUidTrans"));
                        if (link.isEmpty()) {
                            skipUnsupported(row, "an incomplete transfer");
                        } else {
                            transferOut.put(link, row);
                        }
                    }
                    case TYPE_TRANSFER_IN -> {
                        // The outgoing half carries the accounts we need.
                    }
                    default -> skipUnsupported(row,
                            "an entry type Wealthora does not have");
                }
            }
            int transferIndex = 0;
            for (Map<String, Object> row : transferOut.values()) {
                addTransfer(row, ++transferIndex);
            }
        }

        private void addExpense(Map<String, Object> row, int index) {
            LocalDate date = date(row);
            BigDecimal amount = amount(row);
            Account account = accountsByUid.get(text(row.get("assetUid")));
            if (date == null || amount == null || account == null) {
                skipUnsupported(row, "missing account, date, or amount");
                return;
            }
            Category category = categoryFor(row);
            String note = text(row.get("ZCONTENT"));
            String description = note.isEmpty()
                    ? category.getDisplayName() : note;
            try {
                workspace.expenses().add(new Expense("EXPENSE_MM" + index,
                        description, amount, date, category, account,
                        PaymentMethod.UNSPECIFIED, List.of(), note));
            } catch (RuntimeException failure) {
                skipped++;
                warnings.add("An expense dated " + date
                        + " could not be imported: " + reason(failure));
            }
        }

        private void addIncome(Map<String, Object> row, int index) {
            LocalDate date = date(row);
            BigDecimal amount = amount(row);
            Account account = accountsByUid.get(text(row.get("assetUid")));
            if (date == null || amount == null || account == null) {
                skipUnsupported(row, "missing account, date, or amount");
                return;
            }
            String note = text(row.get("ZCONTENT"));
            String source = firstNonEmpty(
                    categoryNameFor(row), note, "Income");
            try {
                workspace.income().add(new Income("INCOME_MM" + index, date,
                        amount, source, account, PaymentMethod.UNSPECIFIED,
                        List.of(), note));
            } catch (RuntimeException failure) {
                skipped++;
                warnings.add("An income entry dated " + date
                        + " could not be imported: " + reason(failure));
            }
        }

        private void addTransfer(Map<String, Object> row, int index) {
            LocalDate date = date(row);
            BigDecimal amount = amount(row);
            Account from = accountsByUid.get(text(row.get("assetUid")));
            Account to = accountsByUid.get(text(row.get("toAssetUid")));
            if (date == null || amount == null || from == null || to == null
                    || from.getIdentifier().equals(to.getIdentifier())) {
                skipUnsupported(row, "missing or identical transfer accounts");
                return;
            }
            try {
                workspace.transfers().add(new Transfer("TRANSFER_MM" + index,
                        date, amount, from, to, List.of(),
                        text(row.get("ZCONTENT"))));
            } catch (RuntimeException failure) {
                skipped++;
                warnings.add("A transfer dated " + date
                        + " could not be imported: " + reason(failure));
            }
        }

        private void skipUnsupported(Map<String, Object> row, String because) {
            skipped++;
            if (warnings.size() < 40) {
                String date = text(row.get("WDATE"));
                warnings.add("Skipped a record"
                        + (date.isEmpty() ? "" : " dated " + date)
                        + " with " + because + ".");
            }
        }

        private Category categoryFor(Map<String, Object> row) {
            Category mapped = categoriesByUid.get(text(row.get("ctgUid")));
            if (mapped != null) {
                return mapped;
            }
            String name = text(row.get("CATEGORY_NAME"));
            if (!name.isEmpty()) {
                Category byName = categoriesByName.get(key(name));
                if (byName != null) {
                    return byName;
                }
                Category builtIn = builtInFor(name);
                if (builtIn != null) {
                    return builtIn;
                }
            }
            unmappedCategories++;
            return Category.OTHER;
        }

        private String categoryNameFor(Map<String, Object> row) {
            Category mapped = categoriesByUid.get(text(row.get("ctgUid")));
            return mapped != null
                    ? mapped.getDisplayName()
                    : text(row.get("CATEGORY_NAME"));
        }

        private List<Map<String, Object>> rows(String table) {
            return database.hasTable(table) ? database.rows(table) : List.of();
        }

        private static LocalDate date(Map<String, Object> row) {
            String value = text(row.get("WDATE"));
            try {
                return value.length() >= 10
                        ? LocalDate.parse(value.substring(0, 10))
                        : null;
            } catch (DateTimeParseException exception) {
                return null;
            }
        }

        private static BigDecimal amount(Map<String, Object> row) {
            String value = text(row.get("ZMONEY"));
            if (value.isEmpty()) {
                return null;
            }
            try {
                BigDecimal parsed = new BigDecimal(value.replace(",", ""))
                        .abs().setScale(2, java.math.RoundingMode.HALF_UP);
                return parsed.signum() <= 0 ? null : parsed;
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        /** Reuses a built-in category when the imported name clearly matches. */
        private static Category builtInFor(String name) {
            return switch (key(name)) {
                case "food", "food & drink", "food and drink", "meal",
                     "meals", "groceries" -> Category.FOOD;
                case "transport", "transportation", "travel", "commute"
                        -> Category.TRANSPORT;
                case "shopping", "clothing" -> Category.SHOPPING;
                case "bills", "utilities", "bills & utilities", "household"
                        -> Category.BILLS;
                case "health", "medical", "healthcare" -> Category.HEALTH;
                case "education", "study", "books" -> Category.EDUCATION;
                case "entertainment", "leisure", "culture" ->
                        Category.ENTERTAINMENT;
                case "other", "others", "etc" -> Category.OTHER;
                default -> null;
            };
        }

        private static String iconFor(AccountType type) {
            return switch (type) {
                case CASH -> "cash";
                case BANK -> "bank";
                case SAVINGS -> "savings";
                case MOBILE_BANKING -> "mobile";
                case DIGITAL_WALLET -> "wallet";
                case CREDIT_CARD, DEBIT_CARD -> "card";
                case OTHER -> Account.DEFAULT_ICON;
            };
        }

        private static String firstNonEmpty(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.strip();
                }
            }
            return "Income";
        }

        private static String reason(RuntimeException failure) {
            String message = failure.getMessage();
            return message == null || message.isBlank()
                    ? "the value was not valid" : message;
        }

        private static String key(String value) {
            return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        }

        private static String text(Object value) {
            return value == null ? "" : value.toString().strip();
        }
    }
}
