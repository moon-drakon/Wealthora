package com.spendwise.voice;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceTransactionParser {

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\w-])(\\d[\\d,]*(?:\\.\\d+)?)\\s*"
            + "(k|thousand|taka|bdt|usd|eur|gbp)\\b");
    private static final Pattern ISO_DATE = Pattern.compile(
            "\\b(20\\d{2}-\\d{2}-\\d{2})\\b");
    private static final Pattern COMMON_DATE = Pattern.compile(
            "\\b(\\d{1,2}[/-]\\d{1,2}[/-]20\\d{2})\\b");
    private static final Pattern TIME = Pattern.compile(
            "\\bat\\s+([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern NOTE = Pattern.compile(
            "\\b(?:with\\s+)?note\\s+(.+)$");
    private static final Pattern TAGS = Pattern.compile(
            "\\btags?\\s+([a-z0-9,_ -]+)$");

    private final List<Account> accounts;
    private final List<Category> categories;
    private final Clock clock;
    private final VoiceCommandNormalizer normalizer;

    public VoiceTransactionParser(
            List<Account> accounts, List<Category> categories) {
        this(accounts, categories, Clock.systemDefaultZone(),
                new VoiceCommandNormalizer());
    }

    public VoiceTransactionParser(
            List<Account> accounts,
            List<Category> categories,
            Clock clock,
            VoiceCommandNormalizer normalizer) {
        this.accounts = List.copyOf(Objects.requireNonNull(accounts));
        this.categories = List.copyOf(Objects.requireNonNull(categories));
        this.clock = Objects.requireNonNull(clock);
        this.normalizer = Objects.requireNonNull(normalizer);
    }

    public VoiceParseResult parse(String transcript) {
        String original = transcript == null ? "" : transcript.strip();
        String command = normalizer.normalize(original);
        VoiceTransactionDraft draft = new VoiceTransactionDraft();
        List<String> warnings = new ArrayList<>();
        if (command.isEmpty()) {
            warnings.add("Type or speak a transaction command.");
            return new VoiceParseResult(original, draft, warnings);
        }

        draft.setTransactionType(detectType(command));
        if (draft.getTransactionType() == null) {
            warnings.add("Transaction type could not be determined.");
        }
        parseAmount(command, draft, warnings);
        parseDateAndTime(command, draft, warnings);
        parseRecurrence(command, draft);
        parseAccounts(command, draft, warnings);
        parseCategory(command, draft, warnings);
        parseNoteAndTags(command, draft);
        draft.setPaymentMethod(paymentMethod(draft.getSourceAccount()));
        draft.setDescription(description(command, draft));
        if (draft.getNextDueDate() == null && draft.isRecurring()) {
            draft.setNextDueDate(draft.getDate());
        }
        return new VoiceParseResult(original, draft, warnings);
    }

    private static TransactionType detectType(String command) {
        if (containsWord(command, "transfer")) return TransactionType.TRANSFER;
        if (containsWord(command, "income")
                || containsWord(command, "salary")
                || containsWord(command, "deposit")) {
            return TransactionType.INCOME;
        }
        if (containsWord(command, "expense")
                || containsWord(command, "spent")
                || containsWord(command, "bill")) {
            return TransactionType.EXPENSE;
        }
        return null;
    }

    private static void parseAmount(
            String command,
            VoiceTransactionDraft draft,
            List<String> warnings) {
        Matcher matcher = AMOUNT.matcher(command);
        if (!matcher.find()) {
            warnings.add("Amount could not be recognized.");
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(matcher.group(1).replace(",", ""));
            String unit = matcher.group(2);
            if (unit.equals("k") || unit.equals("thousand")) {
                amount = amount.multiply(new BigDecimal("1000"));
            }
            draft.setAmount(amount);
            draft.setCurrencyCode(switch (unit) {
                case "usd", "eur", "gbp" -> unit.toUpperCase(Locale.ROOT);
                default -> Account.DEFAULT_CURRENCY_CODE;
            });
        } catch (NumberFormatException exception) {
            warnings.add("Amount format is invalid.");
        }
    }

    private void parseDateAndTime(
            String command,
            VoiceTransactionDraft draft,
            List<String> warnings) {
        LocalDate today = LocalDate.now(clock);
        if (containsWord(command, "yesterday")) {
            draft.setDate(today.minusDays(1));
        } else if (containsWord(command, "today")) {
            draft.setDate(today);
        } else {
            Matcher iso = ISO_DATE.matcher(command);
            Matcher common = COMMON_DATE.matcher(command);
            try {
                if (iso.find()) {
                    draft.setDate(LocalDate.parse(iso.group(1)));
                } else if (common.find()) {
                    String date = common.group(1).replace('-', '/');
                    draft.setDate(LocalDate.parse(date,
                            DateTimeFormatter.ofPattern("d/M/uuuu")));
                } else {
                    draft.setDate(today);
                }
            } catch (DateTimeParseException exception) {
                warnings.add("Date could not be recognized.");
            }
        }
        Matcher time = TIME.matcher(command);
        draft.setTime(time.find()
                ? LocalTime.of(Integer.parseInt(time.group(1)),
                        Integer.parseInt(time.group(2)))
                : LocalTime.now(clock).withSecond(0).withNano(0));
    }

    private static void parseRecurrence(
            String command, VoiceTransactionDraft draft) {
        RecurrenceFrequency frequency = null;
        if (command.contains("every day") || containsWord(command, "daily")) {
            frequency = RecurrenceFrequency.DAILY;
        } else if (command.contains("every week")
                || containsWord(command, "weekly")) {
            frequency = RecurrenceFrequency.WEEKLY;
        } else if (command.contains("every month")
                || containsWord(command, "monthly")) {
            frequency = RecurrenceFrequency.MONTHLY;
        } else if (command.contains("every year")
                || containsWord(command, "yearly")) {
            frequency = RecurrenceFrequency.YEARLY;
        }
        draft.setRecurring(frequency != null || containsWord(command, "recurring"));
        draft.setRecurringFrequency(frequency);
    }

    private void parseAccounts(
            String command,
            VoiceTransactionDraft draft,
            List<String> warnings) {
        List<Mention<Account>> mentions = accountMentions(command);
        TransactionType type = draft.getTransactionType();
        if (type == TransactionType.TRANSFER) {
            if (mentions.size() >= 2) {
                draft.setSourceAccount(mentions.get(0).value());
                draft.setDestinationAccount(mentions.get(1).value());
                if (mentions.size() > 2) {
                    warnings.add("More than two accounts were recognized; review both transfer accounts.");
                }
            } else if (mentions.size() == 1) {
                draft.setSourceAccount(mentions.get(0).value());
                warnings.add("Transfer destination account was not recognized.");
            } else {
                addGenericAccountWarning(command, warnings);
            }
        } else if (mentions.size() == 1) {
            draft.setSourceAccount(mentions.get(0).value());
        } else if (mentions.size() > 1) {
            warnings.add("Multiple accounts were recognized; select the intended account.");
        } else {
            addGenericAccountWarning(command, warnings);
        }
    }

    private List<Mention<Account>> accountMentions(String command) {
        List<Mention<Account>> mentions = new ArrayList<>();
        for (Account account : accounts) {
            String name = normalizer.normalize(account.getDisplayName());
            Matcher matcher = Pattern.compile(
                    "(?<![a-z0-9])" + Pattern.quote(name)
                    + "(?![a-z0-9])").matcher(command);
            while (matcher.find()) {
                mentions.add(new Mention<>(matcher.start(), account));
            }
        }
        mentions.sort(Comparator.comparingInt(Mention::position));
        return mentions;
    }

    private void addGenericAccountWarning(
            String command, List<String> warnings) {
        if (containsWord(command, "bank")) {
            List<Account> bankMatches = accounts.stream()
                    .filter(account -> normalizer.normalize(
                            account.getDisplayName()).contains("bank"))
                    .toList();
            if (bankMatches.size() > 1) {
                warnings.add("Account reference 'bank' is ambiguous; choose an exact account.");
                return;
            }
            if (bankMatches.size() == 1) {
                warnings.add("Select the exact bank account before confirming.");
                return;
            }
        }
        warnings.add("Account could not be matched to an existing account.");
    }

    private void parseCategory(
            String command,
            VoiceTransactionDraft draft,
            List<String> warnings) {
        if (draft.getTransactionType() != TransactionType.EXPENSE) return;
        List<Category> matches = categories.stream()
                .filter(category -> containsPhrase(command,
                        normalizer.normalize(category.getDisplayName())))
                .toList();
        if (matches.isEmpty() && containsWord(command, "internet")) {
            matches = categories.stream()
                    .filter(category -> category.getIdentifier().equals("BILLS"))
                    .toList();
        }
        if (matches.size() != 1) {
            warnings.add(matches.isEmpty()
                    ? "Category could not be matched to an existing category."
                    : "Category reference is ambiguous; select one category.");
            return;
        }
        Category match = matches.get(0);
        if (match.isSubcategory()) {
            draft.setSubcategory(match);
            categories.stream()
                    .filter(category -> category.getIdentifier().equals(
                            match.getParentIdentifier().orElse("")))
                    .findFirst().ifPresent(draft::setCategory);
        } else {
            draft.setCategory(match);
        }
    }

    private static void parseNoteAndTags(
            String command, VoiceTransactionDraft draft) {
        Matcher note = NOTE.matcher(command);
        if (note.find()) draft.setNote(note.group(1));
        Matcher tags = TAGS.matcher(command);
        if (tags.find()) {
            draft.setTags(List.of(tags.group(1).split("[, ]+")));
        }
    }

    private static String description(
            String command, VoiceTransactionDraft draft) {
        if (containsWord(command, "salary")) return "Salary";
        if (containsWord(command, "internet")) return "Internet bill";
        if (draft.getEffectiveCategory() != null) {
            return draft.getEffectiveCategory().getDisplayName();
        }
        if (draft.getTransactionType() == TransactionType.TRANSFER) {
            return draft.getNote().isBlank()
                    ? "Account transfer" : draft.getNote();
        }
        return draft.getTransactionType() == TransactionType.INCOME
                ? "Income" : draft.getTransactionType() == TransactionType.EXPENSE
                        ? "Expense" : "";
    }

    private static PaymentMethod paymentMethod(Account account) {
        if (account == null) return PaymentMethod.UNSPECIFIED;
        AccountType type = account.getType();
        return switch (type) {
            case CASH -> PaymentMethod.CASH;
            case BANK, SAVINGS -> PaymentMethod.BANK_TRANSFER;
            case MOBILE_BANKING -> PaymentMethod.MOBILE_BANKING;
            case DIGITAL_WALLET -> PaymentMethod.DIGITAL_WALLET;
            case CREDIT_CARD -> PaymentMethod.CREDIT_CARD;
            case DEBIT_CARD -> PaymentMethod.DEBIT_CARD;
            case OTHER -> PaymentMethod.OTHER;
        };
    }

    private static boolean containsWord(String value, String word) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(word)
                + "(?![a-z0-9])").matcher(value).find();
    }

    private static boolean containsPhrase(String value, String phrase) {
        return containsWord(value, phrase);
    }

    private record Mention<T>(int position, T value) {
    }
}
