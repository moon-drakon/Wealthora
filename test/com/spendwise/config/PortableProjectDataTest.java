package com.spendwise.config;

import com.spendwise.model.AccountType;
import com.spendwise.service.AccountService;
import com.spendwise.service.FinanceWorkspace;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class PortableProjectDataTest {

    private int passed;

    public static void main(String[] args) throws Exception {
        new PortableProjectDataTest().run();
    }

    private void run() throws Exception {
        test("project root with spaces", this::projectRootWithSpaces);
        test("portable layout and exclusive lock", this::layoutAndLock);
        test("non-directory data target fails safely",
                this::invalidDataTargetFailsSafely);
        test("approved legacy import is non-destructive",
                this::approvedLegacyImport);
        test("declined legacy import stays declined",
                this::declinedLegacyImport);
        test("existing portable data blocks migration",
                this::existingDataBlocksMigration);
        test("invalid legacy data rolls back", this::invalidDataRollsBack);
        System.out.println("All " + passed
                + " portable project-data tests passed.");
    }

    private void projectRootWithSpaces() throws Exception {
        Path project = Files.createTempDirectory("Wealthora Project ");
        String previous = System.getProperty(AppPaths.PROJECT_ROOT_PROPERTY);
        try {
            createProjectMarkers(project);
            System.setProperty(AppPaths.PROJECT_ROOT_PROPERTY,
                    project.toString());
            AppPaths.resetForTests();
            assertEquals(project.toAbsolutePath().normalize(),
                    AppPaths.getProjectRoot());
            assertEquals(project.resolve("data").toAbsolutePath().normalize(),
                    AppPaths.getDataRootDirectory());
            assertEquals(project.resolve("data").toAbsolutePath().normalize(),
                    AppPaths.getDataDirectory());

            Path nested = Files.createDirectories(
                    project.resolve("nested folder").resolve("child"));
            assertEquals(project.toAbsolutePath().normalize(),
                    AppPaths.resolveProjectRoot(nested, null, null));
        } finally {
            restoreProperty(previous);
            AppPaths.resetForTests();
            deleteTree(project);
        }
    }

    private void layoutAndLock() throws Exception {
        Path root = Files.createTempDirectory("wealthora-portable-lock-");
        Path data = root.resolve("data");
        try {
            ProjectDataLock.ensureLayout(data);
            for (String directory : new String[]{
                    "auth", "users", "backups", "settings", "presentation"}) {
                assertTrue(Files.isDirectory(data.resolve(directory)));
            }
            try (ProjectDataLock ignored = ProjectDataLock.acquire(data)) {
                expect(IllegalStateException.class,
                        () -> ProjectDataLock.acquire(data));
            }
            try (ProjectDataLock ignored = ProjectDataLock.acquire(data)) {
                assertTrue(Files.isRegularFile(data.resolve(".wealthora.lock")));
            }
        } finally {
            deleteTree(root);
        }
    }

    private void approvedLegacyImport() throws Exception {
        Path root = Files.createTempDirectory("wealthora-migration-");
        Path portable = root.resolve("project").resolve("data");
        Path legacy = root.resolve("legacy");
        try {
            Files.createDirectories(portable);
            FinanceWorkspace oldWorkspace = FinanceWorkspace.overDirectory(
                    legacy.resolve("data"));
            new AccountService(oldWorkspace.accounts(),
                    oldWorkspace.accountPreference()).addAccount(
                            "Legacy Wallet", AccountType.CASH,
                            new BigDecimal("125.00"));
            LegacyAppDataImporter importer = new LegacyAppDataImporter(
                    portable, legacy);
            assertTrue(importer.shouldOfferMigration());
            LegacyAppDataImporter.MigrationResult result =
                    importer.importLegacyData();
            assertTrue(result.imported());
            assertTrue(result.copiedFileCount() >= 1);
            assertTrue(Files.isRegularFile(portable.resolve("accounts.csv")));
            assertTrue(Files.readString(portable.resolve("accounts.csv"))
                    .contains("Legacy Wallet"));
            assertTrue(Files.isRegularFile(portable.resolve("settings")
                    .resolve("appdata-migration-v1.properties")));
            assertFalse(importer.shouldOfferMigration());
        } finally {
            deleteTree(root);
        }
    }

    private void invalidDataTargetFailsSafely() throws Exception {
        Path root = Files.createTempDirectory("wealthora-invalid-data-root-");
        try {
            Path data = root.resolve("data");
            Files.writeString(data, "must remain a file");
            expect(IllegalStateException.class,
                    () -> ProjectDataLock.acquire(data));
            assertEquals("must remain a file", Files.readString(data));
        } finally {
            deleteTree(root);
        }
    }

    private void declinedLegacyImport() throws Exception {
        Path root = Files.createTempDirectory("wealthora-decline-");
        Path portable = root.resolve("project").resolve("data");
        Path legacy = root.resolve("legacy");
        try {
            Files.createDirectories(portable);
            Files.createDirectories(legacy.resolve("data"));
            Files.writeString(legacy.resolve("data").resolve("expenses.csv"),
                    "invalid but never imported");
            LegacyAppDataImporter importer = new LegacyAppDataImporter(
                    portable, legacy);
            assertTrue(importer.shouldOfferMigration());
            importer.declineMigration();
            assertFalse(importer.shouldOfferMigration());
            assertFalse(Files.exists(portable.resolve("expenses.csv")));
            assertTrue(Files.readString(portable.resolve("settings")
                    .resolve("appdata-migration-v1.properties"))
                    .contains("result=skipped"));
        } finally {
            deleteTree(root);
        }
    }

    private void existingDataBlocksMigration() throws Exception {
        Path root = Files.createTempDirectory("wealthora-existing-");
        Path portable = root.resolve("project").resolve("data");
        Path legacy = root.resolve("legacy");
        try {
            Files.createDirectories(portable);
            Files.writeString(portable.resolve("expenses.csv"), "keep me");
            Files.createDirectories(legacy.resolve("data"));
            Files.writeString(legacy.resolve("data").resolve("expenses.csv"),
                    "old value");
            LegacyAppDataImporter importer = new LegacyAppDataImporter(
                    portable, legacy);
            assertFalse(importer.shouldOfferMigration());
            assertFalse(importer.importLegacyData().imported());
            assertEquals("keep me", Files.readString(
                    portable.resolve("expenses.csv")));
        } finally {
            deleteTree(root);
        }
    }

    private void invalidDataRollsBack() throws Exception {
        Path root = Files.createTempDirectory("wealthora-invalid-old-");
        Path portable = root.resolve("project").resolve("data");
        Path legacy = root.resolve("legacy");
        try {
            Files.createDirectories(portable);
            Files.createDirectories(legacy.resolve("data"));
            Files.writeString(legacy.resolve("data").resolve("accounts.csv"),
                    "not,a,valid,account,file\n");
            LegacyAppDataImporter importer = new LegacyAppDataImporter(
                    portable, legacy);
            assertTrue(importer.shouldOfferMigration());
            expect(RuntimeException.class, importer::importLegacyData);
            assertFalse(Files.exists(portable.resolve("accounts.csv")));
            assertFalse(Files.exists(portable.resolve("settings")
                    .resolve("appdata-migration-v1.properties")));
        } finally {
            deleteTree(root);
        }
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

    private static void createProjectMarkers(Path project) throws Exception {
        Files.writeString(project.resolve("build.xml"), "<project/>");
        Path nbproject = Files.createDirectories(project.resolve("nbproject"));
        Files.writeString(nbproject.resolve("project.xml"), "<project/>");
    }

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(AppPaths.PROJECT_ROOT_PROPERTY);
        } else {
            System.setProperty(AppPaths.PROJECT_ROOT_PROPERTY, previous);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || Files.notExists(root)) {
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
