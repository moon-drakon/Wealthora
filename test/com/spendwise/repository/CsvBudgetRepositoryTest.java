package com.spendwise.repository;

import com.spendwise.config.AppPaths;
import com.spendwise.model.Category;
import com.spendwise.model.MonthlyBudget;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class CsvBudgetRepositoryTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("null path", CsvBudgetRepositoryTest::nullPathIsRejected);
        runTest("constructor side effects", CsvBudgetRepositoryTest::constructorCreatesNothing);
        runTest("missing file", CsvBudgetRepositoryTest::missingFileReturnsEmpty);
        runTest("read directory side effects", CsvBudgetRepositoryTest::readCreatesNoDirectory);
        runTest("first save creation", CsvBudgetRepositoryTest::saveCreatesFile);
        runTest("exact header", CsvBudgetRepositoryTest::headerIsCorrect);
        runTest("overall round trip", CsvBudgetRepositoryTest::overallRoundTrips);
        runTest("category round trip", CsvBudgetRepositoryTest::categoriesRoundTrip);
        runTest("empty limits omitted", CsvBudgetRepositoryTest::emptyLimitsAreNotWritten);
        runTest("two-decimal persistence", CsvBudgetRepositoryTest::amountsPersistExactly);
        runTest("category enum storage", CsvBudgetRepositoryTest::categoryNamesAreStored);
        runTest("multiple months", CsvBudgetRepositoryTest::multipleMonthsPersist);
        runTest("selected replacement", CsvBudgetRepositoryTest::saveReplacesSelectedMonth);
        runTest("other month preservation", CsvBudgetRepositoryTest::savePreservesOtherMonths);
        runTest("chronological months", CsvBudgetRepositoryTest::monthsAreChronological);
        runTest("category enum order", CsvBudgetRepositoryTest::categoriesUseEnumOrder);
        runTest("delete existing", CsvBudgetRepositoryTest::deleteExistingSucceeds);
        runTest("delete missing month", CsvBudgetRepositoryTest::deleteMissingReturnsFalse);
        runTest("delete missing file", CsvBudgetRepositoryTest::deleteMissingCreatesNothing);
        runTest("delete preserves others", CsvBudgetRepositoryTest::deletePreservesOtherMonths);
        runTest("final clear store", CsvBudgetRepositoryTest::finalClearLeavesReadableHeader);
        runTest("malformed header", CsvBudgetRepositoryTest::malformedHeaderIsRejected);
        runTest("malformed month", CsvBudgetRepositoryTest::malformedMonthIsRejected);
        runTest("unknown scope", CsvBudgetRepositoryTest::unknownScopeIsRejected);
        runTest("unknown category", CsvBudgetRepositoryTest::unknownCategoryIsRejected);
        runTest("invalid decimal", CsvBudgetRepositoryTest::invalidDecimalIsRejected);
        runTest("zero amount", CsvBudgetRepositoryTest::zeroAmountIsRejected);
        runTest("negative amount", CsvBudgetRepositoryTest::negativeAmountIsRejected);
        runTest("duplicate overall", CsvBudgetRepositoryTest::duplicateOverallIsRejected);
        runTest("duplicate category", CsvBudgetRepositoryTest::duplicateCategoryIsRejected);
        runTest("corrupt save protection", CsvBudgetRepositoryTest::corruptFileIsNotOverwritten);
        runTest("failed write preservation", CsvBudgetRepositoryTest::failedWritePreservesFile);
        runTest("temporary replacement safety", CsvBudgetRepositoryTest::failedWriteLeavesNoTemp);
        runTest("expense file isolation", CsvBudgetRepositoryTest::expenseFileIsUntouched);
        runTest("test path isolation", CsvBudgetRepositoryTest::testsUseNoProductionPath);

        System.out.println("All " + passedTests + " budget repository tests passed.");
    }

    private static void nullPathIsRejected() {
        expectThrows(NullPointerException.class, () -> new CsvBudgetRepository(null));
    }

    private static void constructorCreatesNothing() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("nested").resolve("budgets.csv");
            new CsvBudgetRepository(path);
            assertFalse(Files.exists(path), "Constructor created the budget file.");
            assertFalse(Files.exists(path.getParent()), "Constructor created a directory.");
        });
    }

    private static void missingFileReturnsEmpty() throws Exception {
        withDirectory(directory -> assertTrue(
                new CsvBudgetRepository(directory.resolve("budgets.csv"))
                        .findByMonth(JULY)
                        .isEmpty(),
                "Missing file should return Optional.empty()."));
    }

    private static void readCreatesNoDirectory() throws Exception {
        withDirectory(directory -> {
            Path parent = directory.resolve("nested");
            new CsvBudgetRepository(parent.resolve("budgets.csv")).findByMonth(JULY);
            assertFalse(Files.exists(parent), "Read created a missing directory.");
        });
    }

    private static void saveCreatesFile() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("nested").resolve("budgets.csv");
            new CsvBudgetRepository(path).save(overall(JULY, "100.00"));
            assertTrue(Files.isRegularFile(path), "First save did not create the file.");
        });
    }

    private static void headerIsCorrect() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            new CsvBudgetRepository(path).save(overall(JULY, "100.00"));
            assertTrue(read(path).startsWith(CsvBudgetRepository.HEADER + "\n"),
                    "Budget CSV header is incorrect.");
        });
    }

    private static void overallRoundTrips() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "15000.00"));
            assertMoney(
                    "15000.00",
                    repository.findByMonth(JULY)
                            .orElseThrow()
                            .getOverallLimit()
                            .orElseThrow(),
                    "Overall limit changed during round trip.");
        });
    }

    private static void categoriesRoundTrip() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(categories(JULY));
            MonthlyBudget actual = repository.findByMonth(JULY).orElseThrow();
            assertMoney("5000.00", actual.getCategoryLimit(Category.FOOD).orElseThrow(),
                    "Food limit changed.");
            assertMoney(
                    "2500.00",
                    actual.getCategoryLimit(Category.TRANSPORT).orElseThrow(),
                    "Transport limit changed.");
        });
    }

    private static void emptyLimitsAreNotWritten() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(new MonthlyBudget(
                    JULY,
                    Optional.empty(),
                    Map.of(Category.FOOD, new BigDecimal("5.00"))));
            String text = read(directory.resolve("budgets.csv"));
            assertFalse(text.contains(",OVERALL,"), "Unset overall limit was written.");
            assertFalse(text.contains(",BILLS,"), "Unset category limit was written.");
        });
    }

    private static void amountsPersistExactly() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            new CsvBudgetRepository(path).save(overall(JULY, "100"));
            assertTrue(read(path).contains(",100.00\n"),
                    "Amount was not persisted with exactly two decimals.");
        });
    }

    private static void categoryNamesAreStored() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            new CsvBudgetRepository(path).save(categories(JULY));
            assertTrue(read(path).contains(",CATEGORY,TRANSPORT,2500.00"),
                    "Category enum name was not stored.");
            assertFalse(read(path).contains("Transport"),
                    "Category display name was stored.");
        });
    }

    private static void multipleMonthsPersist() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "10.00"));
            repository.save(overall(AUGUST, "20.00"));
            assertTrue(repository.findByMonth(JULY).isPresent(), "July was lost.");
            assertTrue(repository.findByMonth(AUGUST).isPresent(), "August was lost.");
        });
    }

    private static void saveReplacesSelectedMonth() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "10.00"));
            repository.save(overall(JULY, "99.00"));
            assertMoney(
                    "99.00",
                    repository.findByMonth(JULY)
                            .orElseThrow()
                            .getOverallLimit()
                            .orElseThrow(),
                    "Selected month was not replaced.");
        });
    }

    private static void savePreservesOtherMonths() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "10.00"));
            repository.save(overall(AUGUST, "20.00"));
            repository.save(overall(JULY, "30.00"));
            assertMoney(
                    "20.00",
                    repository.findByMonth(AUGUST)
                            .orElseThrow()
                            .getOverallLimit()
                            .orElseThrow(),
                    "Saving July changed August.");
        });
    }

    private static void monthsAreChronological() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            CsvBudgetRepository repository = new CsvBudgetRepository(path);
            repository.save(overall(AUGUST, "20.00"));
            repository.save(overall(JULY, "10.00"));
            String text = read(path);
            assertTrue(text.indexOf("2026-07") < text.indexOf("2026-08"),
                    "Output months are not chronological.");
        });
    }

    private static void categoriesUseEnumOrder() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            new CsvBudgetRepository(path).save(new MonthlyBudget(
                    JULY,
                    Optional.empty(),
                    Map.of(
                            Category.OTHER, new BigDecimal("8.00"),
                            Category.FOOD, new BigDecimal("1.00"),
                            Category.BILLS, new BigDecimal("4.00"))));
            String text = read(path);
            assertTrue(text.indexOf(",FOOD,") < text.indexOf(",BILLS,"),
                    "FOOD should precede BILLS.");
            assertTrue(text.indexOf(",BILLS,") < text.indexOf(",OTHER,"),
                    "BILLS should precede OTHER.");
        });
    }

    private static void deleteExistingSucceeds() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "10.00"));
            assertTrue(repository.delete(JULY), "Existing month was not deleted.");
            assertTrue(repository.findByMonth(JULY).isEmpty(), "Deleted month remains.");
        });
    }

    private static void deleteMissingReturnsFalse() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "10.00"));
            byte[] before = Files.readAllBytes(directory.resolve("budgets.csv"));
            assertFalse(repository.delete(AUGUST), "Missing delete should return false.");
            assertBytes(before, Files.readAllBytes(directory.resolve("budgets.csv")),
                    "Missing delete rewrote the file.");
        });
    }

    private static void deleteMissingCreatesNothing() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("nested").resolve("budgets.csv");
            assertFalse(new CsvBudgetRepository(path).delete(JULY),
                    "Missing delete should return false.");
            assertFalse(Files.exists(path.getParent()),
                    "Missing delete created a directory.");
        });
    }

    private static void deletePreservesOtherMonths() throws Exception {
        withDirectory(directory -> {
            CsvBudgetRepository repository = repository(directory);
            repository.save(overall(JULY, "10.00"));
            repository.save(overall(AUGUST, "20.00"));
            repository.delete(JULY);
            assertTrue(repository.findByMonth(JULY).isEmpty(), "July remains.");
            assertTrue(repository.findByMonth(AUGUST).isPresent(), "August was deleted.");
        });
    }

    private static void finalClearLeavesReadableHeader() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            CsvBudgetRepository repository = new CsvBudgetRepository(path);
            repository.save(overall(JULY, "10.00"));
            repository.delete(JULY);
            assertEquals(CsvBudgetRepository.HEADER + "\n", read(path),
                    "Final clear should leave a header-only store.");
            assertTrue(repository.findByMonth(JULY).isEmpty(),
                    "Header-only store should be empty.");
        });
    }

    private static void malformedHeaderIsRejected() throws Exception {
        assertCorrupt("date,scope,category,amount\n");
        assertCorrupt("");
    }

    private static void malformedMonthIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026/07,OVERALL,,10.00\n");
    }

    private static void unknownScopeIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,UNKNOWN,,10.00\n");
    }

    private static void unknownCategoryIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,CATEGORY,UNKNOWN,10.00\n");
    }

    private static void invalidDecimalIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,OVERALL,,10.0\n");
    }

    private static void zeroAmountIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,OVERALL,,0.00\n");
    }

    private static void negativeAmountIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,OVERALL,,-1.00\n");
    }

    private static void duplicateOverallIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,OVERALL,,10.00"
                + "\n2026-07,OVERALL,,20.00\n");
    }

    private static void duplicateCategoryIsRejected() throws Exception {
        assertCorrupt(CsvBudgetRepository.HEADER
                + "\n2026-07,CATEGORY,FOOD,10.00"
                + "\n2026-07,CATEGORY,FOOD,20.00\n");
    }

    private static void corruptFileIsNotOverwritten() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            String corrupt = "bad header\nimportant existing bytes";
            write(path, corrupt);
            expectThrows(
                    RepositoryException.class,
                    () -> new CsvBudgetRepository(path).save(overall(JULY, "10.00")));
            assertEquals(corrupt, read(path), "Corrupted file was overwritten.");
        });
    }

    private static void failedWritePreservesFile() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            CsvBudgetRepository normal = new CsvBudgetRepository(path);
            normal.save(overall(JULY, "10.00"));
            byte[] before = Files.readAllBytes(path);

            expectThrows(
                    RepositoryException.class,
                    () -> new FailingMoveRepository(path)
                            .save(overall(JULY, "99.00")));

            assertBytes(before, Files.readAllBytes(path),
                    "Failed replacement changed the prior valid file.");
        });
    }

    private static void failedWriteLeavesNoTemp() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            new CsvBudgetRepository(path).save(overall(JULY, "10.00"));
            expectThrows(
                    RepositoryException.class,
                    () -> new FailingMoveRepository(path)
                            .save(overall(JULY, "20.00")));
            try (Stream<Path> paths = Files.list(directory)) {
                assertEquals(
                        1L,
                        paths.filter(Files::isRegularFile).count(),
                        "Failed replacement left a temporary file.");
            }
        });
    }

    private static void expenseFileIsUntouched() throws Exception {
        withDirectory(directory -> {
            Path expensePath = directory.resolve("expenses.csv");
            byte[] sentinel = "expense sentinel".getBytes(StandardCharsets.UTF_8);
            Files.write(expensePath, sentinel);
            CsvBudgetRepository repository =
                    new CsvBudgetRepository(directory.resolve("budgets.csv"));
            repository.save(overall(JULY, "10.00"));
            repository.delete(JULY);
            assertBytes(sentinel, Files.readAllBytes(expensePath),
                    "Budget repository touched expenses.csv.");
        });
    }

    private static void testsUseNoProductionPath() throws Exception {
        withDirectory(directory -> {
            Path testPath = directory.resolve("budgets.csv").toAbsolutePath().normalize();
            assertFalse(testPath.equals(AppPaths.getBudgetCsvPath()),
                    "Repository test selected the production budget path.");
            new CsvBudgetRepository(testPath).save(overall(JULY, "10.00"));
        });
    }

    private static void assertCorrupt(String content) throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("budgets.csv");
            write(path, content);
            expectThrows(
                    RepositoryException.class,
                    () -> new CsvBudgetRepository(path).findByMonth(JULY));
        });
    }

    private static CsvBudgetRepository repository(Path directory) {
        return new CsvBudgetRepository(directory.resolve("budgets.csv"));
    }

    private static MonthlyBudget overall(YearMonth month, String amount) {
        return new MonthlyBudget(
                month, Optional.of(new BigDecimal(amount)), Map.of());
    }

    private static MonthlyBudget categories(YearMonth month) {
        return new MonthlyBudget(
                month,
                Optional.empty(),
                Map.of(
                        Category.FOOD, new BigDecimal("5000.00"),
                        Category.TRANSPORT, new BigDecimal("2500.00")));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void withDirectory(DirectoryTest test) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-budget-repository-");
        try {
            test.run(directory);
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError(
                    "Budget repository test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, TestCase action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError("Expected " + expectedType.getSimpleName() + ".");
    }

    private static void assertMoney(
            String expected, BigDecimal actual, String message) {
        assertEquals(new BigDecimal(expected), actual, message);
        assertEquals(2, actual.scale(), message + " Scale should be two.");
    }

    private static void assertBytes(
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

    private static void assertEquals(
            Object expected, Object actual, String message) {
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
    private interface DirectoryTest {

        void run(Path directory) throws Exception;
    }

    private static final class FailingMoveRepository
            extends CsvBudgetRepository {

        FailingMoveRepository(Path path) {
            super(path);
        }

        @Override
        void replaceWithTemporaryFile(Path temporaryFile) throws IOException {
            throw new IOException("Injected replacement failure.");
        }
    }
}
