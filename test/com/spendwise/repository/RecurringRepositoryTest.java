package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;

public final class RecurringRepositoryTest {

    private static final Account BANK = Account.createCustom(
            "ACCOUNT_RECUR_REPO_BANK",
            "Repository Bank",
            AccountType.BANK,
            new BigDecimal("0.00"),
            false);
    private static int passed;

    private RecurringRepositoryTest() {
    }

    public static void main(String[] args) throws Exception {
        test("missing recurring file is read only", RecurringRepositoryTest::missingFile);
        test("expense CSV round trip", RecurringRepositoryTest::expenseRoundTrip);
        test("income optional end round trip", RecurringRepositoryTest::incomeRoundTrip);
        test("transfer CSV round trip", RecurringRepositoryTest::transferRoundTrip);
        test("quoted description round trip", RecurringRepositoryTest::quotedDescription);
        test("recurring update", RecurringRepositoryTest::update);
        test("duplicate add rejected", RecurringRepositoryTest::duplicateAdd);
        test("bad recurring header rejected", RecurringRepositoryTest::badHeader);
        test("bad recurring status rejected", RecurringRepositoryTest::badStatus);
        test("unknown recurring account rejected", RecurringRepositoryTest::unknownAccount);
        test("duplicate stored IDs rejected", RecurringRepositoryTest::duplicateStoredId);
        test("corrupt file preserved on update", RecurringRepositoryTest::corruptPreserved);
        System.out.println(
                "All " + passed + " recurring repository tests passed.");
    }

    private static void missingFile() throws Exception {
        withRepository((repository, path) -> {
            assertTrue(repository.findAll().isEmpty());
            assertFalse(Files.exists(path));
        });
    }

    private static void expenseRoundTrip() throws Exception {
        withRepository((repository, path) -> {
            RecurringEntry expected = entry(
                    "RECURRING_REPO_EXPENSE",
                    RecurringEntryType.EXPENSE,
                    Category.FOOD,
                    null,
                    null,
                    true);
            repository.add(expected);
            RecurringEntry actual = repository.findById(
                    expected.getIdentifier()).orElseThrow();
            assertEntry(expected, actual);
            assertTrue(Files.readString(path, StandardCharsets.UTF_8)
                    .startsWith(CsvRecurringEntryRepository.HEADER + "\n"));
        });
    }

    private static void incomeRoundTrip() throws Exception {
        withRepository((repository, path) -> {
            RecurringEntry expected = entry(
                    "RECURRING_REPO_INCOME",
                    RecurringEntryType.INCOME,
                    null,
                    null,
                    LocalDate.of(2024, 12, 31),
                    true);
            repository.add(expected);
            RecurringEntry actual = repository.findAll().get(0);
            assertEquals(expected.getEndDate(), actual.getEndDate());
            assertEquals(RecurringEntryType.INCOME, actual.getType());
        });
    }

    private static void transferRoundTrip() throws Exception {
        withRepository((repository, path) -> {
            RecurringEntry expected = entry(
                    "RECURRING_REPO_TRANSFER",
                    RecurringEntryType.TRANSFER,
                    null,
                    BANK,
                    null,
                    true);
            repository.add(expected);
            RecurringEntry actual = repository.findAll().get(0);
            assertEquals(BANK, actual.getDestinationAccount().orElseThrow());
            assertEntry(expected, actual);
        });
    }

    private static void quotedDescription() throws Exception {
        withRepository((repository, path) -> {
            RecurringEntry expected = new RecurringEntry(
                    "RECURRING_REPO_QUOTED",
                    RecurringEntryType.INCOME,
                    new BigDecimal("10.00"),
                    "Pay, \"bonus\"",
                    null,
                    Account.DEFAULT,
                    null,
                    RecurrenceFrequency.MONTHLY,
                    1,
                    LocalDate.of(2024, 1, 1),
                    null,
                    LocalDate.of(2024, 1, 1),
                    true);
            repository.add(expected);
            assertEquals(expected.getDescription(),
                    repository.findAll().get(0).getDescription());
        });
    }

    private static void update() throws Exception {
        withRepository((repository, path) -> {
            RecurringEntry original = entry(
                    "RECURRING_REPO_UPDATE",
                    RecurringEntryType.EXPENSE,
                    Category.FOOD,
                    null,
                    null,
                    true);
            repository.add(original);
            RecurringEntry replacement = new RecurringEntry(
                    original.getIdentifier(),
                    original.getType(),
                    new BigDecimal("99.00"),
                    "Updated",
                    Category.BILLS,
                    Account.DEFAULT,
                    null,
                    RecurrenceFrequency.WEEKLY,
                    2,
                    original.getStartDate(),
                    null,
                    original.getNextDueDate(),
                    false);
            repository.update(replacement);
            assertEntry(replacement, repository.findAll().get(0));
        });
    }

    private static void duplicateAdd() throws Exception {
        withRepository((repository, path) -> {
            RecurringEntry entry = entry(
                    "RECURRING_REPO_DUPLICATE",
                    RecurringEntryType.INCOME,
                    null,
                    null,
                    null,
                    true);
            repository.add(entry);
            expect(RepositoryException.class, () -> repository.add(entry));
        });
    }

    private static void badHeader() throws Exception {
        withRepository((repository, path) -> {
            Files.writeString(path, "wrong,header\n", StandardCharsets.UTF_8);
            expect(RepositoryException.class, repository::findAll);
        });
    }

    private static void badStatus() throws Exception {
        withRepository((repository, path) -> {
            Files.writeString(path,
                    CsvRecurringEntryRepository.HEADER + "\n"
                    + row("RECURRING_BAD_STATUS", "UNKNOWN"),
                    StandardCharsets.UTF_8);
            expect(RepositoryException.class, repository::findAll);
        });
    }

    private static void unknownAccount() throws Exception {
        withRepository((repository, path) -> {
            Files.writeString(path,
                    CsvRecurringEntryRepository.HEADER + "\n"
                    + "RECURRING_UNKNOWN_ACCOUNT,INCOME,1.00,Pay,,"
                    + "ACCOUNT_MISSING,,MONTHLY,1,2024-01-01,,2024-01-01,ACTIVE\n",
                    StandardCharsets.UTF_8);
            expect(RepositoryException.class, repository::findAll);
        });
    }

    private static void duplicateStoredId() throws Exception {
        withRepository((repository, path) -> {
            String storedRow = row("RECURRING_STORED_DUP", "ACTIVE");
            Files.writeString(path,
                    CsvRecurringEntryRepository.HEADER + "\n"
                    + storedRow + storedRow,
                    StandardCharsets.UTF_8);
            expect(RepositoryException.class, repository::findAll);
        });
    }

    private static void corruptPreserved() throws Exception {
        withRepository((repository, path) -> {
            String invalid = "not,a,valid,recurring,file\n";
            Files.writeString(path, invalid, StandardCharsets.UTF_8);
            RecurringEntry replacement = entry(
                    "RECURRING_REPO_REPLACE",
                    RecurringEntryType.INCOME,
                    null,
                    null,
                    null,
                    true);
            expect(RepositoryException.class,
                    () -> repository.update(replacement));
            assertEquals(invalid,
                    Files.readString(path, StandardCharsets.UTF_8));
        });
    }

    private static String row(String id, String status) {
        return id + ",INCOME,1.00,Pay,," + Account.DEFAULT_IDENTIFIER
                + ",,MONTHLY,1,2024-01-01,,2024-01-01," + status + "\n";
    }

    private static RecurringEntry entry(
            String id,
            RecurringEntryType type,
            Category category,
            Account destination,
            LocalDate endDate,
            boolean active) {
        return new RecurringEntry(
                id,
                type,
                new BigDecimal("25.00"),
                "Repository entry",
                category,
                Account.DEFAULT,
                destination,
                RecurrenceFrequency.MONTHLY,
                1,
                LocalDate.of(2024, 1, 31),
                endDate,
                LocalDate.of(2024, 1, 31),
                active);
    }

    private static void assertEntry(
            RecurringEntry expected, RecurringEntry actual) {
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getCategory(), actual.getCategory());
        assertEquals(expected.getSourceAccount(), actual.getSourceAccount());
        assertEquals(expected.getDestinationAccount(), actual.getDestinationAccount());
        assertEquals(expected.getFrequency(), actual.getFrequency());
        assertEquals(expected.getInterval(), actual.getInterval());
        assertEquals(expected.getStartDate(), actual.getStartDate());
        assertEquals(expected.getEndDate(), actual.getEndDate());
        assertEquals(expected.getNextDueDate(), actual.getNextDueDate());
        assertEquals(expected.isActive(), actual.isActive());
    }

    private static void withRepository(RepositoryAction action)
            throws Exception {
        Path directory = Files.createTempDirectory("spendwise-recurring-repo-");
        try {
            Path path = directory.resolve("recurring.csv");
            CsvRecurringEntryRepository repository =
                    new CsvRecurringEntryRepository(
                            path,
                            identifier -> {
                                if (Category.isBuiltInIdentifier(identifier)) {
                                    return Category.valueOf(identifier);
                                }
                                throw new RepositoryException(
                                        "Unknown test category: " + identifier);
                            },
                            identifier -> {
                                if (Account.DEFAULT_IDENTIFIER.equals(identifier)) {
                                    return Account.DEFAULT;
                                }
                                if (BANK.getIdentifier().equals(identifier)) {
                                    return BANK;
                                }
                                throw new RepositoryException(
                                        "Unknown test account: " + identifier);
                            });
            action.run(repository, path);
        } finally {
            deleteRecursively(directory);
        }
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
            Class<T> expected, ThrowingRunnable action) throws Exception {
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

    @FunctionalInterface
    private interface RepositoryAction {

        void run(CsvRecurringEntryRepository repository, Path path)
                throws Exception;
    }
}
