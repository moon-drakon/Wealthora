package com.spendwise.service;

import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static int passed;

    private BackupServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        test("backup includes all managed files", BackupServiceTest::allFiles);
        test("backup permits missing optional files", BackupServiceTest::partialFiles);
        test("backup manifest is correct", BackupServiceTest::manifest);
        test("pre-rebrand backup remains compatible",
                BackupServiceTest::legacyManifest);
        test("existing backup requires overwrite", BackupServiceTest::existingDestination);
        test("confirmed backup replacement", BackupServiceTest::confirmedReplacement);
        test("corrupted backup is rejected", BackupServiceTest::corrupted);
        test("unsupported version is rejected", BackupServiceTest::unsupportedVersion);
        test("ZIP traversal is rejected", BackupServiceTest::pathTraversal);
        test("malformed managed CSV is rejected", BackupServiceTest::malformedCsv);
        test("restore validation precedes mutation", BackupServiceTest::validationBeforeMutation);
        test("successful restore replaces snapshot", BackupServiceTest::successfulRestore);
        test("restore removes absent managed files", BackupServiceTest::removesAbsent);
        test("restore preserves unrelated files", BackupServiceTest::preservesUnrelated);
        test("restore creates safety backup", BackupServiceTest::safetyBackup);
        test("safety backup contains pre-restore data", BackupServiceTest::safetyContents);
        test("empty backup round trip", BackupServiceTest::emptyBackup);
        test("backup destination cannot be production data", BackupServiceTest::destinationSafety);
        System.out.println("All " + passed + " backup/restore tests passed.");
    }

    private static void allFiles() throws Exception {
        withFixture(fixture -> {
            for (String name : ManagedDataFiles.FILE_NAMES) {
                fixture.writeData(name, validContent(name));
            }
            BackupResult result = fixture.service.createBackup(
                    fixture.backup, false);
            assertEquals(ManagedDataFiles.FILE_NAMES, result.includedFiles());
            try (ZipFile zip = new ZipFile(fixture.backup.toFile())) {
                assertEquals(ManagedDataFiles.FILE_NAMES.size() + 1, zip.size());
            }
        });
    }

    private static void partialFiles() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validContent("expenses.csv"));
            fixture.writeData("income.csv", validContent("income.csv"));
            BackupResult result = fixture.service.createBackup(
                    fixture.backup, false);
            assertEquals(List.of("expenses.csv", "income.csv"),
                    result.includedFiles());
        });
    }

    private static void manifest() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validContent("expenses.csv"));
            fixture.service.createBackup(fixture.backup, false);
            try (ZipFile zip = new ZipFile(fixture.backup.toFile())) {
                String text = new String(
                        zip.getInputStream(zip.getEntry(BackupService.MANIFEST_NAME))
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
                assertContains(text, "formatVersion=1\n");
                assertContains(text, "application=Wealthora\n");
                assertContains(text, "createdAt=" + NOW + "\n");
                assertContains(text, "files=expenses.csv\n");
            }
        });
    }

    private static void legacyManifest() throws Exception {
        withFixture(fixture -> {
            writeArchive(fixture.backup,
                    "formatVersion=1\n"
                    + "application=SpendWise Expense Tracker\n"
                    + "createdAt=" + NOW + "\nfiles=\n",
                    null,
                    null);
            assertEquals(List.of(),
                    fixture.service.inspectBackup(fixture.backup)
                            .includedFiles());
        });
    }

    private static void existingDestination() throws Exception {
        withFixture(fixture -> {
            Files.writeString(fixture.backup, "keep", StandardCharsets.UTF_8);
            expect(ValidationException.class,
                    () -> fixture.service.createBackup(fixture.backup, false));
            assertEquals("keep", Files.readString(fixture.backup));
        });
    }

    private static void confirmedReplacement() throws Exception {
        withFixture(fixture -> {
            Files.writeString(fixture.backup, "replace", StandardCharsets.UTF_8);
            fixture.writeData("expenses.csv", validContent("expenses.csv"));
            fixture.service.createBackup(fixture.backup, true);
            assertEquals(List.of("expenses.csv"),
                    fixture.service.inspectBackup(fixture.backup).includedFiles());
        });
    }

    private static void corrupted() throws Exception {
        withFixture(fixture -> {
            Files.writeString(fixture.backup, "not a ZIP", StandardCharsets.UTF_8);
            expect(ValidationException.class,
                    () -> fixture.service.inspectBackup(fixture.backup));
        });
    }

    private static void unsupportedVersion() throws Exception {
        withFixture(fixture -> {
            writeArchive(fixture.backup,
                    "formatVersion=99\n"
                    + "application=SpendWise Expense Tracker\n"
                    + "createdAt=" + NOW + "\nfiles=\n",
                    null,
                    null);
            expect(ValidationException.class,
                    () -> fixture.service.inspectBackup(fixture.backup));
        });
    }

    private static void pathTraversal() throws Exception {
        withFixture(fixture -> {
            writeArchive(fixture.backup,
                    "formatVersion=1\n"
                    + "application=SpendWise Expense Tracker\n"
                    + "createdAt=" + NOW + "\nfiles=\n",
                    "../expenses.csv",
                    "evil");
            expect(ValidationException.class,
                    () -> fixture.service.inspectBackup(fixture.backup));
            assertFalse(Files.exists(fixture.root.resolve("expenses.csv")));
        });
    }

    private static void malformedCsv() throws Exception {
        withFixture(fixture -> {
            writeArchive(fixture.backup,
                    "formatVersion=1\n"
                    + "application=SpendWise Expense Tracker\n"
                    + "createdAt=" + NOW + "\nfiles=expenses.csv\n",
                    "expenses.csv",
                    "wrong,header\n");
            expect(ValidationException.class,
                    () -> fixture.service.inspectBackup(fixture.backup));
        });
    }

    private static void validationBeforeMutation() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validExpense("original", "Original"));
            Files.writeString(fixture.backup, "broken", StandardCharsets.UTF_8);
            expect(ValidationException.class,
                    () -> fixture.service.restoreBackup(fixture.backup));
            assertEquals(validExpense("original", "Original"),
                    fixture.readData("expenses.csv"));
            assertEquals(1L, countFiles(fixture.data));
        });
    }

    private static void successfulRestore() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validExpense("backup", "Backup"));
            fixture.writeData("categories.csv", validContent("categories.csv"));
            fixture.service.createBackup(fixture.backup, false);
            fixture.writeData("expenses.csv", validExpense("changed", "Changed"));
            fixture.writeData("categories.csv", "id,name,status\nCUSTOM_TEST,Test,ACTIVE\n");
            RestoreResult result = fixture.service.restoreBackup(fixture.backup);
            assertEquals(validExpense("backup", "Backup"),
                    fixture.readData("expenses.csv"));
            assertEquals(validContent("categories.csv"),
                    fixture.readData("categories.csv"));
            assertEquals(List.of("expenses.csv", "categories.csv"),
                    result.restoredFiles());
        });
    }

    private static void removesAbsent() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validExpense("backup", "Backup"));
            fixture.service.createBackup(fixture.backup, false);
            fixture.writeData("income.csv", validContent("income.csv"));
            fixture.service.restoreBackup(fixture.backup);
            assertFalse(Files.exists(fixture.data.resolve("income.csv")));
        });
    }

    private static void preservesUnrelated() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validExpense("backup", "Backup"));
            fixture.service.createBackup(fixture.backup, false);
            Files.writeString(
                    fixture.data.resolve("notes.txt"),
                    "unrelated",
                    StandardCharsets.UTF_8);
            fixture.service.restoreBackup(fixture.backup);
            assertEquals("unrelated", Files.readString(
                    fixture.data.resolve("notes.txt")));
        });
    }

    private static void safetyBackup() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validExpense("backup", "Backup"));
            fixture.service.createBackup(fixture.backup, false);
            fixture.writeData("expenses.csv", validExpense("current", "Current"));
            RestoreResult result = fixture.service.restoreBackup(fixture.backup);
            Path safety = result.safetyBackup().orElseThrow();
            assertTrue(Files.isRegularFile(safety));
            assertTrue(safety.getFileName().toString()
                    .startsWith("Wealthora-safety-"));
        });
    }

    private static void safetyContents() throws Exception {
        withFixture(fixture -> {
            fixture.writeData("expenses.csv", validExpense("backup", "Backup"));
            fixture.service.createBackup(fixture.backup, false);
            String current = validExpense("current", "Pre restore current");
            fixture.writeData("expenses.csv", current);
            Path safety = fixture.service.restoreBackup(fixture.backup)
                    .safetyBackup().orElseThrow();
            try (ZipFile zip = new ZipFile(safety.toFile())) {
                String saved = new String(
                        zip.getInputStream(zip.getEntry("expenses.csv"))
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
                assertEquals(current, saved);
            }
        });
    }

    private static void emptyBackup() throws Exception {
        withFixture(fixture -> {
            BackupResult backup = fixture.service.createBackup(
                    fixture.backup, false);
            assertTrue(backup.includedFiles().isEmpty());
            RestoreResult restore = fixture.service.restoreBackup(fixture.backup);
            assertTrue(restore.restoredFiles().isEmpty());
            assertTrue(restore.safetyBackup().isEmpty());
        });
    }

    private static void destinationSafety() throws Exception {
        withFixture(fixture -> expect(ValidationException.class, () ->
            fixture.service.createBackup(
                    fixture.data.resolve("backup.zip"), false)));
    }

    private static void writeArchive(
            Path target,
            String manifest,
            String extraName,
            String extraContent) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry(BackupService.MANIFEST_NAME));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (extraName != null) {
                zip.putNextEntry(new ZipEntry(extraName));
                zip.write(extraContent.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String validContent(String name) {
        return switch (name) {
            case "expenses.csv" ->
                "id,description,amount,date,category,notes\n";
            case "budgets.csv" -> "month,scope,category,amount\n";
            case "budget-plans.csv" -> "id,name,startDate,endDate,scope,"
                + "category,amount,rollover,status\n";
            case "savings-goals.csv" -> "recordType,id,goalId,name,"
                + "targetAmount,targetDate,account,date,amount,note,status\n";
            case "debts.csv" -> "recordType,id,debtId,direction,counterparty,"
                + "originalAmount,dueDate,date,amount,note\n";
            case "categories.csv" -> "id,name,status\n";
            case "accounts.csv" -> "id,name,type,openingBalance,status\n";
            case "account-settings.csv" -> "key,value\n"
                + "DEFAULT_ACCOUNT,ACCOUNT_DEFAULT_CASH\n";
            case "income.csv" -> "id,date,amount,source,account,note\n";
            case "transfers.csv" ->
                "id,date,amount,sourceAccount,destinationAccount,note\n";
            case "recurring.csv" ->
                "id,type,amount,description,category,sourceAccount,"
                + "destinationAccount,frequency,interval,startDate,endDate,"
                + "nextDueDate,status\n";
            case "cards.csv" -> "id,name,bank,type,lastFour,creditLimit,"
                + "billingDay,dueDay,cardAccount,paymentAccount,status\n";
            case "currency-settings.csv" -> "currency\nBDT\n";
            default -> throw new IllegalArgumentException(
                    "Unknown managed test file: " + name);
        };
    }

    private static String validExpense(String id, String description) {
        return "id,description,amount,date,category,notes\n"
                + id + "," + description + ",1.00,2024-01-01,FOOD,\n";
    }

    private static long countFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static final class Fixture {

        private final Path root;
        private final Path data;
        private final Path backup;
        private final BackupService service;

        private Fixture(Path root) {
            this.root = root;
            data = root.resolve("data");
            backup = root.resolve("backup.zip");
            service = new BackupService(
                    data, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private void writeData(String name, String content) throws IOException {
            Files.createDirectories(data);
            Files.writeString(
                    data.resolve(name), content, StandardCharsets.UTF_8);
        }

        private String readData(String name) throws IOException {
            return Files.readString(data.resolve(name), StandardCharsets.UTF_8);
        }
    }

    private static void withFixture(FixtureAction action) throws Exception {
        Path root = Files.createTempDirectory("spendwise-backup-test-");
        try {
            action.run(new Fixture(root));
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
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

    private static void assertContains(String actual, String part) {
        if (!actual.contains(part)) {
            throw new AssertionError(
                    "Expected <" + actual + "> to contain <" + part + ">.");
        }
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
    private interface FixtureAction {

        void run(Fixture fixture) throws Exception;
    }
}
