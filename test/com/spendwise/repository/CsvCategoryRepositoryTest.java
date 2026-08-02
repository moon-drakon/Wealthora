package com.spendwise.repository;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.service.CategoryService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

public final class CsvCategoryRepositoryTest {

    private static int passedTests;

    private CsvCategoryRepositoryTest() {
    }

    public static void main(String[] args) {
        run("null path", CsvCategoryRepositoryTest::nullPathIsRejected);
        run("constructor side effects", CsvCategoryRepositoryTest::constructorCreatesNothing);
        run("missing file", CsvCategoryRepositoryTest::missingFileIsEmpty);
        run("missing file immutable", CsvCategoryRepositoryTest::missingSnapshotIsImmutable);
        run("read side effects", CsvCategoryRepositoryTest::viewCreatesNothing);
        run("first mutation", CsvCategoryRepositoryTest::firstMutationCreatesStore);
        run("exact header", CsvCategoryRepositoryTest::headerIsExact);
        run("CSV round trip", CsvCategoryRepositoryTest::csvRoundTripPreservesFields);
        run("archived round trip", CsvCategoryRepositoryTest::archivedStatusRoundTrips);
        run("record order", CsvCategoryRepositoryTest::recordOrderIsPreserved);
        run("quoted names", CsvCategoryRepositoryTest::quotedNamesRoundTrip);
        run("optional BOM", CsvCategoryRepositoryTest::optionalBomIsAccepted);
        run("CRLF records", CsvCategoryRepositoryTest::crlfRecordsAreAccepted);
        run("duplicate identifier", CsvCategoryRepositoryTest::duplicateIdentifierIsRejected);
        run("duplicate name", CsvCategoryRepositoryTest::duplicateNameIsRejected);
        run("built-in name duplicate", CsvCategoryRepositoryTest::builtInNameDuplicateIsRejected);
        run("invalid header", CsvCategoryRepositoryTest::incorrectHeaderIsRejected);
        run("invalid record", CsvCategoryRepositoryTest::invalidRecordIsRejected);
        run("invalid status", CsvCategoryRepositoryTest::invalidStatusIsRejected);
        run("corruption preservation", CsvCategoryRepositoryTest::corruptStoreIsNotOverwritten);
        run("successful temp cleanup", CsvCategoryRepositoryTest::successfulWriteLeavesNoTemp);
        run("replacement fallback", CsvCategoryRepositoryTest::fallbackReplacementWorks);
        run("failed replacement cleanup", CsvCategoryRepositoryTest::failedMoveCleansOnlyTemp);
        run("read-only failure", CsvCategoryRepositoryTest::readOnlyFailurePreservesTarget);
        run("legacy expense compatibility", CsvCategoryRepositoryTest::legacyExpenseLoads);
        run("legacy budget compatibility", CsvCategoryRepositoryTest::legacyBudgetLoads);
        run("custom expense round trip", CsvCategoryRepositoryTest::customExpenseRoundTrips);
        run("custom budget round trip", CsvCategoryRepositoryTest::customBudgetRoundTrips);
        run("production path isolation", CsvCategoryRepositoryTest::testUsesOnlyTemporaryPath);
        System.out.println(
                "All " + passedTests + " category persistence tests passed.");
    }

    private static void nullPathIsRejected() {
        expectThrows(NullPointerException.class,
                () -> new CsvCategoryRepository(null));
    }

    private static void constructorCreatesNothing() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("nested").resolve("categories.csv");
            new CsvCategoryRepository(path);
            assertFalse(Files.exists(path), "Constructor created categories.csv.");
            assertFalse(Files.exists(path.getParent()), "Constructor created its directory.");
        });
    }

    private static void missingFileIsEmpty() throws Exception {
        withDirectory(directory -> {
            List<Category> categories = repository(directory).findAll();
            assertTrue(categories.isEmpty(), "Missing file was not an empty store.");
        });
    }

    private static void missingSnapshotIsImmutable() throws Exception {
        withDirectory(directory -> {
            List<Category> categories = repository(directory).findAll();
            expectThrows(UnsupportedOperationException.class,
                    () -> categories.add(custom("CUSTOM_001", "Travel")));
        });
    }

    private static void viewCreatesNothing() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("data").resolve("categories.csv");
            new CsvCategoryRepository(path).findAll();
            assertFalse(Files.exists(path), "Read created categories.csv.");
            assertFalse(Files.exists(path.getParent()), "Read created a data directory.");
        });
    }

    private static void firstMutationCreatesStore() throws Exception {
        withDirectory(directory -> {
            Path path = directory.resolve("data").resolve("categories.csv");
            new CsvCategoryRepository(path).add(custom("CUSTOM_001", "Travel"));
            assertTrue(Files.isRegularFile(path), "First mutation did not create the store.");
        });
    }

    private static void headerIsExact() throws Exception {
        withDirectory(directory -> {
            Path path = path(directory);
            repository(directory).add(custom("CUSTOM_001", "Travel"));
            assertTrue(read(path).startsWith(CsvCategoryRepository.HEADER + "\n"),
                    "Category header is incorrect.");
        });
    }

    private static void csvRoundTripPreservesFields() throws Exception {
        withDirectory(directory -> {
            Category expected = custom("CUSTOM_001", "Study supplies");
            CsvCategoryRepository repository = repository(directory);
            repository.add(expected);
            Category actual = repository.findAll().get(0);
            assertEquals(expected.getIdentifier(), actual.getIdentifier(), "ID mismatch.");
            assertEquals(expected.getDisplayName(), actual.getDisplayName(), "Name mismatch.");
            assertTrue(actual.isActive(), "Active status mismatch.");
        });
    }

    private static void archivedStatusRoundTrips() throws Exception {
        withDirectory(directory -> {
            CsvCategoryRepository repository = repository(directory);
            repository.add(custom("CUSTOM_001", "Travel"));
            repository.update(custom("CUSTOM_001", "Travel").withArchived(true));
            assertTrue(repository.findAll().get(0).isArchived(),
                    "Archived status did not round-trip.");
        });
    }

    private static void recordOrderIsPreserved() throws Exception {
        withDirectory(directory -> {
            CsvCategoryRepository repository = repository(directory);
            repository.add(custom("CUSTOM_002", "Second"));
            repository.add(custom("CUSTOM_001", "First"));
            assertEquals(
                    List.of("CUSTOM_002", "CUSTOM_001"),
                    repository.findAll().stream().map(Category::getIdentifier).toList(),
                    "Custom category order changed.");
        });
    }

    private static void quotedNamesRoundTrip() throws Exception {
        withDirectory(directory -> {
            CsvCategoryRepository repository = repository(directory);
            repository.add(custom("CUSTOM_001", "Travel, \"Local\" বাংলা"));
            assertEquals(
                    "Travel, \"Local\" বাংলা",
                    repository.findAll().get(0).getDisplayName(),
                    "Quoted or Unicode category text changed.");
        });
    }

    private static void optionalBomIsAccepted() throws Exception {
        withDirectory(directory -> {
            write(path(directory), "\uFEFF" + CsvCategoryRepository.LEGACY_HEADER
                    + "\nCUSTOM_001,Travel,ACTIVE\n");
            assertEquals("Travel", repository(directory).findAll().get(0).getDisplayName(),
                    "BOM-prefixed file did not load.");
        });
    }

    private static void crlfRecordsAreAccepted() throws Exception {
        withDirectory(directory -> {
            write(path(directory), CsvCategoryRepository.LEGACY_HEADER
                    + "\r\nCUSTOM_001,Travel,ACTIVE\r\n");
            assertEquals(1, repository(directory).findAll().size(),
                    "CRLF category data did not load.");
        });
    }

    private static void duplicateIdentifierIsRejected() throws Exception {
        withDirectory(directory -> {
            Path path = path(directory);
            write(path, CsvCategoryRepository.LEGACY_HEADER
                    + "\nCUSTOM_001,Travel,ACTIVE"
                    + "\nCUSTOM_001,Trips,ARCHIVED\n");
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
        });
    }

    private static void duplicateNameIsRejected() throws Exception {
        withDirectory(directory -> {
            Path path = path(directory);
            write(path, CsvCategoryRepository.LEGACY_HEADER
                    + "\nCUSTOM_001,Travel,ACTIVE"
                    + "\nCUSTOM_002,tRaVeL,ARCHIVED\n");
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
        });
    }

    private static void builtInNameDuplicateIsRejected() throws Exception {
        withDirectory(directory -> {
            write(path(directory), CsvCategoryRepository.LEGACY_HEADER
                    + "\nCUSTOM_001,food,ACTIVE\n");
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
        });
    }

    private static void incorrectHeaderIsRejected() throws Exception {
        withDirectory(directory -> {
            write(path(directory), "name,id,status\nTravel,CUSTOM_001,ACTIVE\n");
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
        });
    }

    private static void invalidRecordIsRejected() throws Exception {
        withDirectory(directory -> {
            write(path(directory), CsvCategoryRepository.LEGACY_HEADER
                    + "\nCUSTOM_001,Travel\n");
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
        });
    }

    private static void invalidStatusIsRejected() throws Exception {
        withDirectory(directory -> {
            write(path(directory), CsvCategoryRepository.LEGACY_HEADER
                    + "\nCUSTOM_001,Travel,DELETED\n");
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
        });
    }

    private static void corruptStoreIsNotOverwritten() throws Exception {
        withDirectory(directory -> {
            Path path = path(directory);
            write(path, "corrupt category data");
            byte[] before = Files.readAllBytes(path);
            expectThrows(RepositoryException.class,
                    () -> repository(directory).add(custom("CUSTOM_001", "Travel")));
            assertArrayEquals(before, Files.readAllBytes(path),
                    "Corrupt category file was changed.");
        });
    }

    private static void successfulWriteLeavesNoTemp() throws Exception {
        withDirectory(directory -> {
            repository(directory).add(custom("CUSTOM_001", "Travel"));
            assertNoRepositoryTemp(directory);
        });
    }

    private static void fallbackReplacementWorks() throws Exception {
        withDirectory(directory -> {
            CsvCategoryRepository repository =
                    new CsvCategoryRepository(path(directory), false);
            repository.add(custom("CUSTOM_001", "Travel"));
            repository.update(custom("CUSTOM_001", "Trips"));
            assertEquals("Trips", repository.findAll().get(0).getDisplayName(),
                    "Fallback replacement did not store the update.");
            assertNoRepositoryTemp(directory);
        });
    }

    private static void failedMoveCleansOnlyTemp() throws Exception {
        withDirectory(directory -> {
            Path target = path(directory);
            Files.createDirectory(target);
            expectThrows(RepositoryException.class,
                    () -> repository(directory).add(custom("CUSTOM_001", "Travel")));
            assertTrue(Files.isDirectory(target), "Existing target directory was removed.");
            assertNoRepositoryTemp(directory);
        });
    }

    private static void readOnlyFailurePreservesTarget() throws Exception {
        withDirectory(directory -> {
            Path target = path(directory);
            Files.createDirectory(target);
            expectThrows(RepositoryException.class,
                    () -> repository(directory).findAll());
            assertTrue(Files.isDirectory(target), "Read failure changed the target.");
        });
    }

    private static void legacyExpenseLoads() throws Exception {
        withDirectory(directory -> {
            Path categoryPath = directory.resolve("categories.csv");
            CategoryService categories =
                    new CategoryService(new CsvCategoryRepository(categoryPath));
            Path expensePath = directory.resolve("expenses.csv");
            write(expensePath,
                    "id,description,amount,date,category,notes\n"
                    + "legacy,Lunch,12.50,2025-01-01,FOOD,\n");
            Expense expense = new CsvExpenseRepository(
                    expensePath, categories::resolveCategory).findAll().get(0);
            assertSame(Category.FOOD, expense.getCategory(),
                    "Legacy expense category was not preserved.");
            assertFalse(Files.exists(categoryPath),
                    "Resolving a built-in created categories.csv.");
        });
    }

    private static void legacyBudgetLoads() throws Exception {
        withDirectory(directory -> {
            Path categoryPath = directory.resolve("categories.csv");
            CategoryService categories =
                    new CategoryService(new CsvCategoryRepository(categoryPath));
            Path budgetPath = directory.resolve("budgets.csv");
            write(budgetPath,
                    "month,scope,category,amount\n"
                    + "2025-01,CATEGORY,FOOD,50.00\n");
            assertEquals(
                    new BigDecimal("50.00"),
                    new CsvBudgetRepository(
                            budgetPath, categories::resolveCategory)
                            .findByMonth(YearMonth.of(2025, 1))
                            .orElseThrow()
                            .getCategoryLimit(Category.FOOD)
                            .orElseThrow(),
                    "Legacy budget category was not preserved.");
        });
    }

    private static void customExpenseRoundTrips() throws Exception {
        withDirectory(directory -> {
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(directory.resolve("categories.csv")));
            Category travel = categories.addCategory("Travel");
            CsvExpenseRepository expenses = new CsvExpenseRepository(
                    directory.resolve("expenses.csv"), categories::resolveCategory);
            expenses.add(new Expense(
                    "custom-expense",
                    "Train",
                    new BigDecimal("15.00"),
                    LocalDate.of(2025, 1, 1),
                    travel,
                    ""));
            assertEquals(travel, expenses.findAll().get(0).getCategory(),
                    "Custom expense category did not round-trip.");
        });
    }

    private static void customBudgetRoundTrips() throws Exception {
        withDirectory(directory -> {
            CategoryService categories = new CategoryService(
                    new CsvCategoryRepository(directory.resolve("categories.csv")));
            Category travel = categories.addCategory("Travel");
            CsvBudgetRepository budgets = new CsvBudgetRepository(
                    directory.resolve("budgets.csv"), categories::resolveCategory);
            budgets.save(new com.spendwise.model.MonthlyBudget(
                    YearMonth.of(2025, 1),
                    java.util.Optional.empty(),
                    java.util.Map.of(travel, new BigDecimal("75.00"))));
            assertEquals(
                    new BigDecimal("75.00"),
                    budgets.findByMonth(YearMonth.of(2025, 1))
                            .orElseThrow()
                            .getCategoryLimit(travel)
                            .orElseThrow(),
                    "Custom budget category did not round-trip.");
        });
    }

    private static void testUsesOnlyTemporaryPath() throws Exception {
        withDirectory(directory -> {
            Path expected = path(directory).toAbsolutePath().normalize();
            repository(directory).add(custom("CUSTOM_001", "Travel"));
            assertTrue(Files.isRegularFile(expected), "Temporary test store is missing.");
            assertFalse(expected.equals(
                    com.spendwise.config.AppPaths.getCategoryCsvPath()),
                    "Persistence test targeted the production category path.");
        });
    }

    private static Category custom(String identifier, String name) {
        return Category.createCustom(identifier, name, false);
    }

    private static CsvCategoryRepository repository(Path directory) {
        return new CsvCategoryRepository(path(directory));
    }

    private static Path path(Path directory) {
        return directory.resolve("categories.csv");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void assertNoRepositoryTemp(Path directory) throws Exception {
        try (var paths = Files.list(directory)) {
            assertFalse(paths.anyMatch(path ->
                    path.getFileName().toString().startsWith(".spendwise-categories-")),
                    "A category repository temporary file was left behind.");
        }
    }

    private static void withDirectory(ThrowingConsumer<Path> action) throws Exception {
        Path directory = Files.createTempDirectory("spendwise-category-test-");
        try {
            action.accept(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
        }
    }

    private static void run(String name, ThrowingRunnable test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError(
                    "Category persistence test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName() + " but received "
                    + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(
                "Expected " + expectedType.getSimpleName() + " to be thrown.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
