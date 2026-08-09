package com.spendwise.imports;

import com.spendwise.service.FinanceWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MoneyManagerImportTest {

    private static final Path FIXTURE = Path.of("test", "resources",
            "foreign-formats", "money-manager-sample.mmbak");
    private int passed;

    public static void main(String[] args) throws Exception {
        new MoneyManagerImportTest().run();
    }

    private void run() throws Exception {
        test("content-based database detection", this::detectsDatabase);
        test("sanitized backup mapping and source integrity",
                this::mapsFixtureWithoutMutation);
        test("ZIP-wrapped database mapping", this::mapsZipFixture);
        test("false extension is rejected", this::rejectsFalseExtension);
        System.out.println("All " + passed
                + " Money Manager import tests passed.");
    }

    private void detectsDatabase() {
        ForeignBackupFormat format = ForeignBackupDetector.detect(FIXTURE);
        assertEquals(ForeignBackupFormat.Kind.MONEY_MANAGER_DATABASE,
                format.kind());
        assertTrue(format.isImportable());
        assertTrue(format.notes().stream().anyMatch(
                note -> note.startsWith("Transactions: 5")));
    }

    private void mapsFixtureWithoutMutation() throws Exception {
        String before = sha256(FIXTURE);
        Path staging = Files.createTempDirectory("wealthora-mm-import-");
        try {
            MoneyManagerImport.Result result = MoneyManagerImport.read(
                    FIXTURE, staging);
            assertMapped(result);
            assertEquals(before, sha256(FIXTURE));
        } finally {
            deleteTree(staging);
        }
    }

    private void mapsZipFixture() throws Exception {
        Path root = Files.createTempDirectory("wealthora-mm-zip-");
        try {
            Path archive = root.resolve("renamed-backup.zip");
            try (ZipOutputStream zip = new ZipOutputStream(
                    Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("nested/sample.sqlite"));
                zip.write(Files.readAllBytes(FIXTURE));
                zip.closeEntry();
            }
            assertEquals(ForeignBackupFormat.Kind.MONEY_MANAGER_DATABASE,
                    ForeignBackupDetector.detect(archive).kind());
            MoneyManagerImport.Result result = MoneyManagerImport.read(
                    archive, root.resolve("mapped"));
            assertMapped(result);
        } finally {
            deleteTree(root);
        }
    }

    private void rejectsFalseExtension() throws Exception {
        Path root = Files.createTempDirectory("wealthora-mm-false-");
        try {
            Path falseBackup = root.resolve("not-a-database.mmbak");
            Files.writeString(falseBackup, "date,amount,description\n");
            ForeignBackupFormat format = ForeignBackupDetector.detect(
                    falseBackup);
            assertEquals(ForeignBackupFormat.Kind.DELIMITED_TEXT,
                    format.kind());
            assertFalse(format.isImportable());
            expect(SqliteFormatException.class,
                    () -> MoneyManagerImport.read(falseBackup,
                            root.resolve("mapped")));
        } finally {
            deleteTree(root);
        }
    }

    private static void assertMapped(MoneyManagerImport.Result result) {
        FinanceWorkspace workspace = result.workspace();
        assertEquals("BDT", result.currencyCode());
        assertEquals(0, result.skippedRecords());
        assertEquals(2, workspace.accounts().findAll().size());
        assertEquals(1, workspace.income().findAll().size());
        assertEquals(2, workspace.expenses().findAll().size());
        assertEquals(1, workspace.transfers().findAll().size());
    }

    private void test(String name, ThrowingAction action) throws Exception {
        try {
            action.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Throwable failure) {
            throw new AssertionError("FAIL: " + name, failure);
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
    }

    private static void deleteTree(Path root) throws Exception {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void expect(
            Class<? extends Throwable> type, ThrowingAction action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Unexpected exception", failure);
            }
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected false.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
