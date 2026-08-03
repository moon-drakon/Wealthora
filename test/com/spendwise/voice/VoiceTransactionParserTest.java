package com.spendwise.voice;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.TransactionType;
import com.spendwise.model.Transfer;
import com.spendwise.repository.AccountPreferenceRepository;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.repository.TransferRepository;
import com.spendwise.service.AccountService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.QuickEntryService;
import com.spendwise.service.TransferService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VoiceTransactionParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T10:15:00Z"), ZoneOffset.UTC);
    private static int passed;

    private VoiceTransactionParserTest() {
    }

    public static void main(String[] args) throws Exception {
        test("English expense command", VoiceTransactionParserTest::expense);
        test("English income and word amount", VoiceTransactionParserTest::income);
        test("English transfer command", VoiceTransactionParserTest::transfer);
        test("recurring command", VoiceTransactionParserTest::recurring);
        test("Banglish command", VoiceTransactionParserTest::banglish);
        test("Bangla expense uses canonical English fields",
                VoiceTransactionParserTest::banglaExpense);
        test("Bangla income and scaled amount",
                VoiceTransactionParserTest::banglaIncome);
        test("Bangla transfer resolves account aliases",
                VoiceTransactionParserTest::banglaTransfer);
        test("Bangla recurring date is normalized",
                VoiceTransactionParserTest::banglaRecurring);
        test("Banglish amount without a currency word",
                VoiceTransactionParserTest::banglishBareAmount);
        test("ambiguous Bangla date remains unresolved",
                VoiceTransactionParserTest::ambiguousBanglaDate);
        test("missing account remains unresolved",
                VoiceTransactionParserTest::missingAccount);
        test("ambiguous account remains unresolved",
                VoiceTransactionParserTest::ambiguousAccount);
        test("invalid amount is rejected",
                VoiceTransactionParserTest::invalidAmount);
        test("same transfer account is rejected",
                VoiceTransactionParserTest::sameTransferAccount);
        test("cancelled draft does not mutate repositories",
                VoiceTransactionParserTest::cancelledDraft);
        test("confirmed draft uses transaction service",
                VoiceTransactionParserTest::confirmedDraft);
        test("manual parser works without speech provider",
                VoiceTransactionParserTest::manualFallback);
        test("speech request uses Bangla locale without storing audio",
                VoiceTransactionParserTest::banglaSpeechRequest);
        test("missing speech provider reports honest status",
                VoiceTransactionParserTest::missingProviderStatus);
        test("authenticated speech clears captured audio",
                VoiceTransactionParserTest::authenticatedSpeechClearsAudio);
        test("speech provider exposes stop and cancel separately",
                VoiceTransactionParserTest::speechStopAndCancel);
        test("automatic speech uses V1-compatible language options",
                VoiceTransactionParserTest::automaticSpeechRequest);
        System.out.println("All " + passed
                + " voice transaction tests passed.");
    }

    private static void expense() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Add expense 450 taka for food from bKash today.").draft();
        assertEquals(TransactionType.EXPENSE, draft.getTransactionType());
        assertMoney("450", draft.getAmount());
        assertEquals("bKash", draft.getSourceAccount().getDisplayName());
        assertEquals(Category.FOOD, draft.getEffectiveCategory());
        assertEquals(TODAY, draft.getDate());
        assertTrue(draft.isComplete());
    }

    private static void income() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Add income five thousand taka salary to Bank Account.").draft();
        assertEquals(TransactionType.INCOME, draft.getTransactionType());
        assertMoney("5000", draft.getAmount());
        assertEquals("Bank Account",
                draft.getSourceAccount().getDisplayName());
        assertEquals("Salary", draft.getDescription());
        assertTrue(draft.isComplete());
    }

    private static void transfer() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Transfer 1,000 taka from bKash to Cash.").draft();
        assertEquals(TransactionType.TRANSFER, draft.getTransactionType());
        assertMoney("1000", draft.getAmount());
        assertEquals("bKash", draft.getSourceAccount().getDisplayName());
        assertEquals("Cash", draft.getDestinationAccount().getDisplayName());
        assertTrue(draft.isComplete());
    }

    private static void recurring() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Add recurring expense 1200 taka internet bill from bKash every month.")
                .draft();
        assertTrue(draft.isRecurring());
        assertEquals(RecurrenceFrequency.MONTHLY,
                draft.getRecurringFrequency());
        assertEquals(TODAY, draft.getNextDueDate());
        assertEquals(Category.BILLS, draft.getEffectiveCategory());
        assertTrue(draft.isComplete());
    }

    private static void banglish() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Food er jonno bKash theke 450 taka expense add koro.").draft();
        assertEquals(TransactionType.EXPENSE, draft.getTransactionType());
        assertMoney("450", draft.getAmount());
        assertEquals("bKash", draft.getSourceAccount().getDisplayName());
        assertEquals(Category.FOOD, draft.getEffectiveCategory());
        assertTrue(draft.isComplete());
    }

    private static void banglaExpense() {
        VoiceParseResult result = parser(baseAccounts()).parse(
                "আজ বিকাশ থেকে খাবারে ৫০০ টাকা খরচ");
        VoiceTransactionDraft draft = result.draft();
        assertEquals(TransactionType.EXPENSE, draft.getTransactionType());
        assertMoney("500", draft.getAmount());
        assertEquals("BDT", draft.getCurrencyCode());
        assertEquals("bKash", draft.getSourceAccount().getDisplayName());
        assertEquals(Category.FOOD, draft.getEffectiveCategory());
        assertEquals("Food", draft.getDescription());
        assertEquals(TODAY, draft.getDate());
        assertTrue(draft.isComplete());
        assertTrue(result.transcript().contains("৫০০"));
    }

    private static void banglaIncome() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "ব্যাংকে ৩০ হাজার টাকা বেতন পেয়েছি").draft();
        assertEquals(TransactionType.INCOME, draft.getTransactionType());
        assertMoney("30000", draft.getAmount());
        assertEquals("BDT", draft.getCurrencyCode());
        assertEquals("Bank Account",
                draft.getSourceAccount().getDisplayName());
        assertEquals("Salary", draft.getDescription());
        assertTrue(draft.isComplete());
    }

    private static void banglaTransfer() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "বিকাশ থেকে ব্যাংকে ১০০০ টাকা ট্রান্সফার").draft();
        assertEquals(TransactionType.TRANSFER, draft.getTransactionType());
        assertMoney("1000", draft.getAmount());
        assertEquals("bKash", draft.getSourceAccount().getDisplayName());
        assertEquals("Bank Account",
                draft.getDestinationAccount().getDisplayName());
        assertEquals("Account transfer", draft.getDescription());
        assertTrue(draft.isComplete());
    }

    private static void banglaRecurring() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "প্রতি মাসের ৫ তারিখ ইন্টারনেট বিল ১২০০ টাকা ব্যাংক থেকে").draft();
        assertEquals(TransactionType.EXPENSE, draft.getTransactionType());
        assertMoney("1200", draft.getAmount());
        assertEquals(Category.BILLS, draft.getEffectiveCategory());
        assertEquals(RecurrenceFrequency.MONTHLY,
                draft.getRecurringFrequency());
        assertEquals(LocalDate.of(2026, 8, 5), draft.getNextDueDate());
        assertTrue(draft.isComplete());
    }

    private static void banglishBareAmount() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "bank e salary 30000 add koro").draft();
        assertEquals(TransactionType.INCOME, draft.getTransactionType());
        assertMoney("30000", draft.getAmount());
        assertEquals("BDT", draft.getCurrencyCode());
        assertEquals("Bank Account",
                draft.getSourceAccount().getDisplayName());
        assertTrue(draft.isComplete());
    }

    private static void ambiguousBanglaDate() {
        VoiceParseResult result = parser(baseAccounts()).parse(
                "কাল বিকাশ থেকে খাবারে ৫০০ টাকা খরচ");
        assertEquals(null, result.draft().getDate());
        assertContains(result.allReviewMessages(), "ambiguous");
        assertFalse(result.draft().isComplete());
    }

    private static void missingAccount() {
        VoiceParseResult result = parser(baseAccounts()).parse(
                "Add expense 450 taka for food today.");
        assertEquals(null, result.draft().getSourceAccount());
        assertContains(result.allReviewMessages(),
                "Account could not be matched");
        assertFalse(result.draft().isComplete());
    }

    private static void ambiguousAccount() {
        List<Account> accounts = new ArrayList<>(baseAccounts());
        accounts.add(account("ACCOUNT_CITY_BANK", "City Bank", AccountType.BANK));
        VoiceParseResult result = parser(accounts).parse(
                "Add expense 450 taka for food from bank today.");
        assertEquals(null, result.draft().getSourceAccount());
        assertContains(result.warnings(), "ambiguous");
    }

    private static void invalidAmount() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Add expense 0 taka for food from Cash today.").draft();
        assertContains(draft.findValidationProblems(),
                "Amount must be greater than zero");
    }

    private static void sameTransferAccount() {
        VoiceTransactionDraft draft = parser(baseAccounts()).parse(
                "Transfer 100 taka from Cash to Cash today.").draft();
        assertContains(draft.findValidationProblems(),
                "Transfer accounts must be different");
    }

    private static void cancelledDraft() {
        Fixture fixture = new Fixture();
        parser(fixture.accounts.listSelectableAccounts()).parse(
                "Add expense 50 taka for food from Cash today.");
        assertTrue(fixture.expenses.entries.isEmpty());
        assertTrue(fixture.income.entries.isEmpty());
        assertTrue(fixture.transfers.entries.isEmpty());
    }

    private static void confirmedDraft() {
        Fixture fixture = new Fixture();
        VoiceTransactionDraft draft = parser(
                fixture.accounts.listSelectableAccounts()).parse(
                "Add expense 75 taka for food from Cash today with note lunch.")
                .draft();
        fixture.quickEntry.confirmVoiceDraft(draft);
        assertEquals(1, fixture.expenses.entries.size());
        Expense saved = fixture.expenses.entries.get(0);
        assertMoney("75", saved.getAmount());
        assertEquals("lunch", saved.getNotes());
    }

    private static void manualFallback() {
        VoiceEntrySettings settings = new VoiceEntrySettings();
        VoiceCaptureService capture = new VoiceCaptureService(
                new UnconfiguredSpeechRecognitionProvider(), settings);
        expect(IllegalStateException.class, capture::capture);
        VoiceTransactionDraft manual = parser(baseAccounts()).parse(
                "Bank account e 5000 taka salary income add koro.").draft();
        assertTrue(manual.isComplete());
        assertEquals(TransactionType.INCOME, manual.getTransactionType());
    }

    private static void banglaSpeechRequest() {
        SpeechRecognitionRequest request =
                SpeechRecognitionRequest.forLanguage(VoiceInputLanguage.BANGLA);
        assertEquals("bn-BD", request.localeTag());
        assertFalse(request.allowLanguageDetection());
        assertFalse(request.storeAudio());
        expect(IllegalArgumentException.class,
                () -> new SpeechRecognitionRequest(
                        VoiceInputLanguage.BANGLA, "bn-BD",
                        java.time.Duration.ofSeconds(30), false, true));
    }

    private static void missingProviderStatus() {
        UnconfiguredSpeechRecognitionProvider provider =
                new UnconfiguredSpeechRecognitionProvider();
        assertEquals(SpeechProviderStatus.NOT_CONFIGURED,
                provider.getProviderStatus());
        assertFalse(provider.isConfigured());
        expect(IllegalStateException.class, () -> provider.recognize(
                SpeechRecognitionRequest.forLanguage(
                        VoiceInputLanguage.AUTOMATIC)));
    }

    private static void authenticatedSpeechClearsAudio() {
        FakeSpeechClient client = new FakeSpeechClient(true);
        FakeMicrophone microphone = new FakeMicrophone();
        AuthenticatedSpeechRecognitionProvider provider =
                new AuthenticatedSpeechRecognitionProvider(client, microphone);
        provider.refreshStatus();
        assertTrue(provider.isConfigured());
        SpeechRecognitionResult result = provider.recognize(
                SpeechRecognitionRequest.forLanguage(
                        VoiceInputLanguage.BANGLISH_MIXED));
        assertEquals("Paid 500 taka for lunch", result.transcript());
        assertEquals(VoiceInputLanguage.ENGLISH, result.detectedLanguage());
        assertTrue(client.receivedNonZeroAudio);
        for (byte value : microphone.audio) assertEquals((byte) 0, value);
    }

    private static void speechStopAndCancel() {
        FakeMicrophone microphone = new FakeMicrophone();
        AuthenticatedSpeechRecognitionProvider provider =
                new AuthenticatedSpeechRecognitionProvider(
                        new FakeSpeechClient(true), microphone);
        provider.stop();
        assertTrue(microphone.stopped);
        assertFalse(microphone.cancelled);
        provider.cancel();
        assertTrue(microphone.cancelled);

        AuthenticatedSpeechRecognitionProvider offline =
                new AuthenticatedSpeechRecognitionProvider(
                        new FakeSpeechClient(false), new FakeMicrophone());
        offline.refreshStatus();
        assertEquals(SpeechProviderStatus.NOT_CONFIGURED,
                offline.getProviderStatus());
    }

    private static void automaticSpeechRequest() {
        SpeechRecognitionRequest automatic =
                SpeechRecognitionRequest.forLanguage(
                        VoiceInputLanguage.AUTOMATIC);
        assertEquals("en-US", automatic.localeTag());
        assertTrue(automatic.allowLanguageDetection());
        SpeechRecognitionRequest mixed =
                SpeechRecognitionRequest.forLanguage(
                        VoiceInputLanguage.BANGLISH_MIXED);
        assertEquals("en-US", mixed.localeTag());
        assertTrue(mixed.allowLanguageDetection());
    }

    private static VoiceTransactionParser parser(List<Account> accounts) {
        return new VoiceTransactionParser(accounts,
                List.of(Category.values()), CLOCK,
                new VoiceCommandNormalizer());
    }

    private static List<Account> baseAccounts() {
        return List.of(
                Account.DEFAULT,
                account("ACCOUNT_BKASH", "bKash", AccountType.MOBILE_BANKING),
                account("ACCOUNT_BANK", "Bank Account", AccountType.BANK));
    }

    private static Account account(
            String identifier, String name, AccountType type) {
        return Account.createCustom(identifier, name, type,
                BigDecimal.ZERO, false);
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

    private static void expect(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName()
                    + " but caught " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + ".");
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertTrue(actual != null);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static void assertContains(List<String> values, String expected) {
        assertTrue(values.stream().anyMatch(value -> value.contains(expected)));
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false.");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected <" + expected
                    + "> but was <" + actual + ">.");
        }
    }

    private static final class Fixture {
        private final MemoryAccountRepository accountRepository =
                new MemoryAccountRepository();
        private final AccountService accounts = new AccountService(
                accountRepository, new MemoryAccountPreferenceRepository());
        private final MemoryExpenseRepository expenses =
                new MemoryExpenseRepository();
        private final MemoryIncomeRepository income = new MemoryIncomeRepository();
        private final MemoryTransferRepository transfers =
                new MemoryTransferRepository();
        private final QuickEntryService quickEntry;

        private Fixture() {
            for (Account account : baseAccounts().stream()
                    .filter(value -> !value.isProtected()).toList()) {
                accountRepository.entries.add(account);
            }
            quickEntry = new QuickEntryService(
                    new ExpenseService(expenses, accounts),
                    new IncomeService(income, accounts),
                    new TransferService(transfers, accounts));
        }
    }

    private static final class MemoryAccountRepository
            implements AccountRepository {
        private final List<Account> entries = new ArrayList<>();
        @Override public List<Account> findAll() { return List.copyOf(entries); }
        @Override public Optional<Account> findById(String identifier) {
            return entries.stream().filter(value -> value.getIdentifier()
                    .equals(identifier)).findFirst();
        }
        @Override public void add(Account account) { entries.add(account); }
        @Override public void update(Account account) {
            entries.replaceAll(value -> value.equals(account) ? account : value);
        }
    }

    private static final class MemoryAccountPreferenceRepository
            implements AccountPreferenceRepository {
        private String identifier = Account.DEFAULT_IDENTIFIER;
        @Override public Optional<String> findDefaultAccountId() {
            return Optional.of(identifier);
        }
        @Override public void saveDefaultAccountId(String value) {
            identifier = value;
        }
    }

    private static final class MemoryExpenseRepository
            implements ExpenseRepository {
        private final List<Expense> entries = new ArrayList<>();
        @Override public List<Expense> findAll() { return List.copyOf(entries); }
        @Override public Optional<Expense> findById(String id) {
            return entries.stream().filter(value -> value.getId().equals(id))
                    .findFirst();
        }
        @Override public void add(Expense expense) { entries.add(expense); }
        @Override public void update(Expense expense) {
            entries.replaceAll(value -> value.equals(expense) ? expense : value);
        }
        @Override public boolean deleteById(String id) {
            return entries.removeIf(value -> value.getId().equals(id));
        }
    }

    private static final class MemoryIncomeRepository implements IncomeRepository {
        private final List<Income> entries = new ArrayList<>();
        @Override public List<Income> findAll() { return List.copyOf(entries); }
        @Override public Optional<Income> findById(String id) {
            return entries.stream().filter(value -> value.getId().equals(id))
                    .findFirst();
        }
        @Override public void add(Income value) { entries.add(value); }
        @Override public void update(Income value) {
            entries.replaceAll(entry -> entry.equals(value) ? value : entry);
        }
        @Override public boolean deleteById(String id) {
            return entries.removeIf(value -> value.getId().equals(id));
        }
    }

    private static final class MemoryTransferRepository
            implements TransferRepository {
        private final List<Transfer> entries = new ArrayList<>();
        @Override public List<Transfer> findAll() { return List.copyOf(entries); }
        @Override public Optional<Transfer> findById(String id) {
            return entries.stream().filter(value -> value.getId().equals(id))
                    .findFirst();
        }
        @Override public void add(Transfer value) { entries.add(value); }
        @Override public void update(Transfer value) {
            entries.replaceAll(entry -> entry.equals(value) ? value : entry);
        }
        @Override public boolean deleteById(String id) {
            return entries.removeIf(value -> value.getId().equals(id));
        }
    }

    private static final class FakeSpeechClient implements SpeechApiClient {
        private final boolean active;
        private boolean receivedNonZeroAudio;

        private FakeSpeechClient(boolean active) {
            this.active = active;
        }

        @Override
        public SpeechBackendStatus getSpeechStatus() {
            return new SpeechBackendStatus(SpeechProviderStatus.READY,
                    "Test provider ready.");
        }

        @Override
        public SpeechRecognitionResult recognizeSpeech(
                byte[] linearPcmAudio, int sampleRateHertz,
                VoiceInputLanguage language) {
            receivedNonZeroAudio = linearPcmAudio[0] != 0;
            return new SpeechRecognitionResult(
                    "Paid 500 taka for lunch", 0.92,
                    VoiceInputLanguage.ENGLISH);
        }

        @Override
        public boolean hasActiveSession() {
            return active;
        }
    }

    private static final class FakeMicrophone implements MicrophoneCapture {
        private final byte[] audio = new byte[3_200];
        private boolean stopped;
        private boolean cancelled;

        private FakeMicrophone() {
            java.util.Arrays.fill(audio, (byte) 7);
        }

        @Override public List<MicrophoneDevice> listMicrophones() {
            return List.of(new MicrophoneDevice("test", "Test microphone"));
        }
        @Override public void selectMicrophone(String identifier) { }
        @Override public String getSelectedMicrophoneIdentifier() {
            return "test";
        }
        @Override public String getStatus() { return "Test microphone ready."; }
        @Override public CapturedAudio capture(java.time.Duration maximum) {
            return new CapturedAudio(audio, java.time.Duration.ofMillis(100));
        }
        @Override public void stop() { stopped = true; }
        @Override public void cancel() { cancelled = true; }
        @Override public java.time.Duration getRecordingDuration() {
            return java.time.Duration.ZERO;
        }
        @Override public boolean testMicrophone() { return true; }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
