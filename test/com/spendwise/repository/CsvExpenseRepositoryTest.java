package com.spendwise.repository;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class CsvExpenseRepositoryTest {

    private static final LocalDate VALID_DATE = LocalDate.now().minusDays(2);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("missing file is empty and immutable", CsvExpenseRepositoryTest::missingFileIsEmpty);
        runTest("zero-byte file is empty", CsvExpenseRepositoryTest::zeroByteFileIsEmpty);
        runTest("header-only file is empty", CsvExpenseRepositoryTest::headerOnlyFileIsEmpty);
        runTest("add creates valid CSV", CsvExpenseRepositoryTest::addCreatesValidCsv);
        runTest("complete round trip", CsvExpenseRepositoryTest::completeRoundTrip);
        runTest("existing ID preservation", CsvExpenseRepositoryTest::existingIdIsPreserved);
        runTest("amount remains exact", CsvExpenseRepositoryTest::amountRemainsExact);
        runTest("insertion order", CsvExpenseRepositoryTest::multipleRecordsPreserveOrder);
        runTest("find existing ID", CsvExpenseRepositoryTest::findByIdFindsExpense);
        runTest("find missing ID", CsvExpenseRepositoryTest::findByIdReturnsEmpty);
        runTest("update preserves position", CsvExpenseRepositoryTest::updatePreservesPosition);
        runTest("delete target only", CsvExpenseRepositoryTest::deleteRemovesOnlyTarget);
        runTest("delete missing ID", CsvExpenseRepositoryTest::deleteMissingIdDoesNotRewrite);
        runTest("duplicate add safety", CsvExpenseRepositoryTest::duplicateAddLeavesBytesUnchanged);
        runTest("missing update safety", CsvExpenseRepositoryTest::missingUpdateLeavesBytesUnchanged);
        runTest("commas and quotes", CsvExpenseRepositoryTest::commasAndQuotesRoundTrip);
        runTest("quoted line breaks", CsvExpenseRepositoryTest::quotedLineBreaksRoundTrip);
        runTest("Unicode text", CsvExpenseRepositoryTest::unicodeRoundTrip);
        runTest("LF record endings", CsvExpenseRepositoryTest::lfInputIsAccepted);
        runTest("CRLF record endings", CsvExpenseRepositoryTest::crlfInputIsAccepted);
        runTest("UTF-8 BOM", CsvExpenseRepositoryTest::bomBeforeHeaderIsAccepted);
        runTest("incorrect header", CsvExpenseRepositoryTest::incorrectHeaderIsRejected);
        runTest("wrong column count", CsvExpenseRepositoryTest::wrongColumnCountIsRejected);
        runTest("unclosed quoted data", CsvExpenseRepositoryTest::unclosedQuoteIsRejected);
        runTest("illegal quote placement", CsvExpenseRepositoryTest::illegalQuoteIsRejected);
        runTest("invalid amount", CsvExpenseRepositoryTest::invalidAmountIsRejected);
        runTest("invalid date", CsvExpenseRepositoryTest::invalidDateIsRejected);
        runTest("invalid category", CsvExpenseRepositoryTest::invalidCategoryIsRejected);
        runTest("invalid model data", CsvExpenseRepositoryTest::invalidModelDataIsRejected);
        runTest("duplicate stored IDs", CsvExpenseRepositoryTest::duplicateStoredIdsAreRejected);
        runTest("corrupt read safety", CsvExpenseRepositoryTest::corruptReadDoesNotModifyFile);
        runTest("temporary-file cleanup", CsvExpenseRepositoryTest::successfulWriteLeavesNoTempFile);
        runTest("header retained after final delete", CsvExpenseRepositoryTest::finalDeleteKeepsHeader);
        runTest("BOM outside header", CsvExpenseRepositoryTest::bomOutsideHeaderIsRejected);
        runTest("blank stored record", CsvExpenseRepositoryTest::blankRecordIsRejected);

        System.out.println("All " + passedTests + " persistence tests passed.");
    }

    private static void missingFileIsEmpty() throws Exception {
        withTempDirectory(directory -> {
            Path nestedDirectory = directory.resolve("data");
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(nestedDirectory.resolve("expenses.csv"));

            List<Expense> expenses = repository.findAll();

            assertTrue(expenses.isEmpty(), "Missing file should load as empty.");
            expectThrows(
                    UnsupportedOperationException.class,
                    () -> expenses.add(createExpense("new-id", "Lunch")),
                    "Returned snapshot should be structurally immutable.");
            assertFalse(
                    Files.exists(nestedDirectory),
                    "Reading a missing file must not create its parent directory.");
        });
    }

    private static void zeroByteFileIsEmpty() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            Files.createFile(csvPath);

            assertTrue(
                    new CsvExpenseRepository(csvPath).findAll().isEmpty(),
                    "Zero-byte file should load as empty.");
        });
    }

    private static void headerOnlyFileIsEmpty() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(csvPath, CsvExpenseCodec.HEADER + "\n");

            assertTrue(
                    new CsvExpenseRepository(csvPath).findAll().isEmpty(),
                    "Header-only file should load as empty.");
        });
    }

    private static void addCreatesValidCsv() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            CsvExpenseRepository repository = new CsvExpenseRepository(csvPath);

            repository.add(createExpense("expense-001", "Lunch"));

            String csvText = Files.readString(csvPath, StandardCharsets.UTF_8);
            assertTrue(csvText.startsWith(CsvExpenseCodec.HEADER + "\n"), "CSV header is missing.");
            assertEquals(2L, csvText.lines().count(), "CSV should contain a header and one record.");
        });
    }

    private static void completeRoundTrip() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            Expense expected = new Expense(
                    "expense-001",
                    "Reference book",
                    new BigDecimal("45.60"),
                    VALID_DATE,
                    Category.EDUCATION,
                    "Second edition");

            repository.add(expected);
            Expense actual = repository.findAll().get(0);

            assertExpenseEquals(expected, actual, "Round trip changed an expense field.");
        });
    }

    private static void existingIdIsPreserved() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(createExpense("existing-id", "Lunch"));

            assertEquals(
                    "existing-id",
                    repository.findAll().get(0).getId(),
                    "Stored ID was not preserved.");
        });
    }

    private static void amountRemainsExact() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(new Expense(
                    "expense-001",
                    "Lunch",
                    new BigDecimal("12.3400"),
                    VALID_DATE,
                    Category.FOOD,
                    ""));

            BigDecimal amount = repository.findAll().get(0).getAmount();
            assertEquals(new BigDecimal("12.34"), amount, "Amount value changed.");
            assertEquals(2, amount.scale(), "Amount scale changed.");
        });
    }

    private static void multipleRecordsPreserveOrder() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(createExpense("expense-001", "First"));
            repository.add(createExpense("expense-002", "Second"));
            repository.add(createExpense("expense-003", "Third"));

            assertIds(
                    repository.findAll(),
                    List.of("expense-001", "expense-002", "expense-003"),
                    "Insertion order changed.");
        });
    }

    private static void findByIdFindsExpense() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(createExpense("expense-001", "Lunch"));
            repository.add(createExpense("expense-002", "Book"));

            Expense expense = repository.findById("expense-002")
                    .orElseThrow(() -> new AssertionError("Expected expense was not found."));
            assertEquals("Book", expense.getDescription(), "Incorrect expense was returned.");
        });
    }

    private static void findByIdReturnsEmpty() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(createExpense("expense-001", "Lunch"));

            assertTrue(
                    repository.findById("missing-id").isEmpty(),
                    "Missing ID should return Optional.empty().");
        });
    }

    private static void updatePreservesPosition() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(createExpense("expense-001", "First"));
            repository.add(createExpense("expense-002", "Second"));
            repository.add(createExpense("expense-003", "Third"));

            repository.update(new Expense(
                    "expense-002",
                    "Updated second",
                    new BigDecimal("99.00"),
                    VALID_DATE,
                    Category.SHOPPING,
                    "Updated"));

            List<Expense> expenses = repository.findAll();
            assertIds(
                    expenses,
                    List.of("expense-001", "expense-002", "expense-003"),
                    "Update changed record position.");
            assertEquals(
                    "Updated second",
                    expenses.get(1).getDescription(),
                    "Matching record was not updated.");
        });
    }

    private static void deleteRemovesOnlyTarget() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            repository.add(createExpense("expense-001", "First"));
            repository.add(createExpense("expense-002", "Second"));
            repository.add(createExpense("expense-003", "Third"));

            assertTrue(repository.deleteById("expense-002"), "Existing ID should be deleted.");
            assertIds(
                    repository.findAll(),
                    List.of("expense-001", "expense-003"),
                    "Delete removed or reordered the wrong records.");
        });
    }

    private static void deleteMissingIdDoesNotRewrite() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            CsvExpenseRepository repository = new CsvExpenseRepository(csvPath);
            repository.add(createExpense("expense-001", "Lunch"));
            byte[] before = Files.readAllBytes(csvPath);

            assertFalse(repository.deleteById("missing-id"), "Missing delete should return false.");

            assertByteArrayEquals(
                    before,
                    Files.readAllBytes(csvPath),
                    "Missing delete should not rewrite the file.");
        });
    }

    private static void duplicateAddLeavesBytesUnchanged() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            CsvExpenseRepository repository = new CsvExpenseRepository(csvPath);
            repository.add(createExpense("expense-001", "Lunch"));
            byte[] before = Files.readAllBytes(csvPath);

            expectRepositoryException(
                    () -> repository.add(createExpense("expense-001", "Duplicate")),
                    "Duplicate add should fail.");

            assertByteArrayEquals(
                    before,
                    Files.readAllBytes(csvPath),
                    "Duplicate add modified the file.");
        });
    }

    private static void missingUpdateLeavesBytesUnchanged() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            CsvExpenseRepository repository = new CsvExpenseRepository(csvPath);
            repository.add(createExpense("expense-001", "Lunch"));
            byte[] before = Files.readAllBytes(csvPath);

            expectRepositoryException(
                    () -> repository.update(createExpense("missing-id", "Missing")),
                    "Missing update should fail.");

            assertByteArrayEquals(
                    before,
                    Files.readAllBytes(csvPath),
                    "Missing update modified the file.");
        });
    }

    private static void commasAndQuotesRoundTrip() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            Expense expected = new Expense(
                    "expense-001",
                    "Dinner, with \"friends\"",
                    new BigDecimal("30.00"),
                    VALID_DATE,
                    Category.FOOD,
                    "Included \"dessert\", drinks");

            repository.add(expected);

            assertExpenseEquals(
                    expected,
                    repository.findAll().get(0),
                    "Comma or quote was not preserved.");
        });
    }

    private static void quotedLineBreaksRoundTrip() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            Expense expected = new Expense(
                    "expense-001",
                    "First line\nSecond line",
                    new BigDecimal("15.00"),
                    VALID_DATE,
                    Category.OTHER,
                    "Windows line\r\nNext line");

            repository.add(expected);

            assertExpenseEquals(
                    expected,
                    repository.findAll().get(0),
                    "Quoted line break was not preserved.");
        });
    }

    private static void unicodeRoundTrip() throws Exception {
        withTempDirectory(directory -> {
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(directory.resolve("expenses.csv"));
            Expense expected = new Expense(
                    "expense-001",
                    "বাংলা বই",
                    new BigDecimal("25.00"),
                    VALID_DATE,
                    Category.EDUCATION,
                    "ক্যাফে ☕");

            repository.add(expected);

            assertExpenseEquals(
                    expected,
                    repository.findAll().get(0),
                    "Unicode text was not preserved.");
        });
    }

    private static void lfInputIsAccepted() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(csvPath, CsvExpenseCodec.HEADER + "\n" + validRecord("expense-001") + "\n");

            assertEquals(
                    1,
                    new CsvExpenseRepository(csvPath).findAll().size(),
                    "LF input should be accepted.");
        });
    }

    private static void crlfInputIsAccepted() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\r\n" + validRecord("expense-001") + "\r\n");

            assertEquals(
                    1,
                    new CsvExpenseRepository(csvPath).findAll().size(),
                    "CRLF input should be accepted.");
        });
    }

    private static void bomBeforeHeaderIsAccepted() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    "\uFEFF" + CsvExpenseCodec.HEADER + "\n"
                    + validRecord("expense-001") + "\n");

            assertEquals(
                    1,
                    new CsvExpenseRepository(csvPath).findAll().size(),
                    "UTF-8 BOM before the header should be accepted.");
        });
    }

    private static void incorrectHeaderIsRejected() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    "identifier,description,amount,date,category,notes\n"
                    + validRecord("expense-001") + "\n");

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Incorrect header should fail.");
        });
    }

    private static void wrongColumnCountIsRejected() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\n"
                    + validRecord("expense-001") + "\n"
                    + "expense-002,Missing notes,10.00," + VALID_DATE + ",FOOD\n");

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Wrong column count should fail the complete read.");
        });
    }

    private static void unclosedQuoteIsRejected() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\n"
                    + "expense-001,\"Unclosed,10.00," + VALID_DATE + ",FOOD,Notes");

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Unclosed quoted field should fail.");
        });
    }

    private static void illegalQuoteIsRejected() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\n"
                    + "expense-001,\"Description\"x,10.00," + VALID_DATE + ",FOOD,Notes\n");

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Illegal quote placement should fail.");
        });
    }

    private static void invalidAmountIsRejected() throws Exception {
        assertCorruptRecordRejected(
                "expense-001,Lunch,not-a-number," + VALID_DATE + ",FOOD,Notes",
                "Invalid amount should fail.");
    }

    private static void invalidDateIsRejected() throws Exception {
        assertCorruptRecordRejected(
                "expense-001,Lunch,10.00,not-a-date,FOOD,Notes",
                "Invalid date should fail.");
    }

    private static void invalidCategoryIsRejected() throws Exception {
        assertCorruptRecordRejected(
                "expense-001,Lunch,10.00," + VALID_DATE + ",UNKNOWN,Notes",
                "Invalid category should fail.");
    }

    private static void invalidModelDataIsRejected() throws Exception {
        assertCorruptRecordRejected(
                "expense-001,,10.00," + VALID_DATE + ",FOOD,Notes",
                "Invalid model data should fail.");
    }

    private static void duplicateStoredIdsAreRejected() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\n"
                    + validRecord("duplicate-id") + "\n"
                    + validRecord("duplicate-id") + "\n");

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Duplicate stored IDs should fail.");
        });
    }

    private static void corruptReadDoesNotModifyFile() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\n"
                    + "expense-001,Bad,invalid," + VALID_DATE + ",FOOD,Notes\n");
            byte[] before = Files.readAllBytes(csvPath);

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Corrupt read should fail.");

            assertByteArrayEquals(
                    before,
                    Files.readAllBytes(csvPath),
                    "Corrupt read modified the original file.");
        });
    }

    private static void successfulWriteLeavesNoTempFile() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            new CsvExpenseRepository(csvPath).add(createExpense("expense-001", "Lunch"));

            try (Stream<Path> paths = Files.list(directory)) {
                List<Path> remaining = paths.toList();
                assertEquals(1, remaining.size(), "Successful write left a temporary file.");
                assertEquals(csvPath, remaining.get(0), "Unexpected file remained after writing.");
            }
        });
    }

    private static void finalDeleteKeepsHeader() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            CsvExpenseRepository repository = new CsvExpenseRepository(csvPath);
            repository.add(createExpense("expense-001", "Lunch"));

            assertTrue(repository.deleteById("expense-001"), "Final expense should be deleted.");
            assertEquals(
                    CsvExpenseCodec.HEADER + "\n",
                    Files.readString(csvPath, StandardCharsets.UTF_8),
                    "Final delete should leave a header-only CSV.");
            assertTrue(repository.findAll().isEmpty(), "Header-only store should load as empty.");
        });
    }

    private static void bomOutsideHeaderIsRejected() throws Exception {
        assertCorruptRecordRejected(
                "expense-001,Lunch,10.00," + VALID_DATE + ",FOOD,No\uFEFFtes",
                "BOM outside the header should fail.");
    }

    private static void blankRecordIsRejected() throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(
                    csvPath,
                    CsvExpenseCodec.HEADER + "\n"
                    + validRecord("expense-001") + "\n\n");

            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    "Blank stored record should not be skipped.");
        });
    }

    private static void assertCorruptRecordRejected(String record, String message)
            throws Exception {
        withTempDirectory(directory -> {
            Path csvPath = directory.resolve("expenses.csv");
            writeText(csvPath, CsvExpenseCodec.HEADER + "\n" + record + "\n");
            expectRepositoryException(
                    () -> new CsvExpenseRepository(csvPath).findAll(),
                    message);
        });
    }

    private static Expense createExpense(String id, String description) {
        return new Expense(
                id,
                description,
                new BigDecimal("12.50"),
                VALID_DATE,
                Category.FOOD,
                "Test note");
    }

    private static String validRecord(String id) {
        return id + ",Lunch,12.50," + VALID_DATE + ",FOOD,Test note";
    }

    private static void assertExpenseEquals(
            Expense expected, Expense actual, String message) {
        assertEquals(expected.getId(), actual.getId(), message + " ID mismatch.");
        assertEquals(
                expected.getDescription(),
                actual.getDescription(),
                message + " Description mismatch.");
        assertEquals(expected.getAmount(), actual.getAmount(), message + " Amount mismatch.");
        assertEquals(expected.getDate(), actual.getDate(), message + " Date mismatch.");
        assertEquals(expected.getCategory(), actual.getCategory(), message + " Category mismatch.");
        assertEquals(expected.getNotes(), actual.getNotes(), message + " Notes mismatch.");
    }

    private static void assertIds(
            List<Expense> expenses, List<String> expectedIds, String message) {
        List<String> actualIds = expenses.stream().map(Expense::getId).toList();
        assertEquals(expectedIds, actualIds, message);
    }

    private static void writeText(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void withTempDirectory(TempDirectoryTest test) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-persistence-test-");
        try {
            test.run(directory);
        } finally {
            deleteTestDirectory(directory);
        }
    }

    private static void deleteTestDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (AssertionError | Exception exception) {
            throw new AssertionError("Persistence test failed: " + name, exception);
        }
    }

    private static void expectRepositoryException(TestCase action, String message)
            throws Exception {
        expectThrows(RepositoryException.class, action, message);
    }

    private static void expectThrows(
            Class<? extends Throwable> expectedType, TestCase action, String message)
            throws Exception {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return;
            }
            throw new AssertionError(
                    message + " Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(
                message + " Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertByteArrayEquals(
            byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }

    @FunctionalInterface
    private interface TempDirectoryTest {

        void run(Path directory) throws Exception;
    }
}
