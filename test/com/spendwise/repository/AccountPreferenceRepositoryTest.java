package com.spendwise.repository;

import com.spendwise.model.Account;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class AccountPreferenceRepositoryTest {

    private static int passed;

    private AccountPreferenceRepositoryTest() {
    }

    public static void main(String[] args) throws Exception {
        test("missing settings file is read only",
                AccountPreferenceRepositoryTest::missingFile);
        test("default account round trip",
                AccountPreferenceRepositoryTest::roundTrip);
        test("default account overwrite",
                AccountPreferenceRepositoryTest::overwrite);
        test("invalid header rejected",
                AccountPreferenceRepositoryTest::invalidHeader);
        test("invalid key rejected",
                AccountPreferenceRepositoryTest::invalidKey);
        test("extra records rejected",
                AccountPreferenceRepositoryTest::extraRecord);
        test("invalid account ID rejected",
                AccountPreferenceRepositoryTest::invalidIdentifier);
        test("corrupt settings preserved on save",
                AccountPreferenceRepositoryTest::corruptPreserved);
        System.out.println("All " + passed
                + " account preference repository tests passed.");
    }

    private static void missingFile() throws Exception {
        withRepository((repository, path) -> {
            assertTrue(repository.findDefaultAccountId().isEmpty());
            assertFalse(Files.exists(path));
        });
    }

    private static void roundTrip() throws Exception {
        withRepository((repository, path) -> {
            repository.saveDefaultAccountId(Account.DEFAULT_IDENTIFIER);
            assertEquals(Account.DEFAULT_IDENTIFIER,
                    repository.findDefaultAccountId().orElseThrow());
            assertEquals(
                    CsvAccountPreferenceRepository.HEADER + "\n"
                    + "DEFAULT_ACCOUNT," + Account.DEFAULT_IDENTIFIER + "\n",
                    Files.readString(path, StandardCharsets.UTF_8));
        });
    }

    private static void overwrite() throws Exception {
        withRepository((repository, path) -> {
            repository.saveDefaultAccountId(Account.DEFAULT_IDENTIFIER);
            repository.saveDefaultAccountId("ACCOUNT_SAVINGS");
            assertEquals("ACCOUNT_SAVINGS",
                    repository.findDefaultAccountId().orElseThrow());
            assertEquals(2, Files.readAllLines(path).size());
        });
    }

    private static void invalidHeader() throws Exception {
        rejects("wrong,header\nDEFAULT_ACCOUNT,ACCOUNT_SAVINGS\n");
    }

    private static void invalidKey() throws Exception {
        rejects(CsvAccountPreferenceRepository.HEADER
                + "\nOTHER,ACCOUNT_SAVINGS\n");
    }

    private static void extraRecord() throws Exception {
        rejects(CsvAccountPreferenceRepository.HEADER
                + "\nDEFAULT_ACCOUNT,ACCOUNT_SAVINGS"
                + "\nDEFAULT_ACCOUNT,ACCOUNT_CASH\n");
    }

    private static void invalidIdentifier() throws Exception {
        rejects(CsvAccountPreferenceRepository.HEADER
                + "\nDEFAULT_ACCOUNT,INVALID\n");
    }

    private static void corruptPreserved() throws Exception {
        withRepository((repository, path) -> {
            String corrupt = "wrong,header\n";
            Files.writeString(path, corrupt, StandardCharsets.UTF_8);
            expect(RepositoryException.class, () ->
                repository.saveDefaultAccountId(Account.DEFAULT_IDENTIFIER));
            assertEquals(corrupt,
                    Files.readString(path, StandardCharsets.UTF_8));
        });
    }

    private static void rejects(String content) throws Exception {
        withRepository((repository, path) -> {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            expect(RepositoryException.class,
                    repository::findDefaultAccountId);
        });
    }

    private static void withRepository(RepositoryAction action)
            throws Exception {
        Path directory = Files.createTempDirectory("spendwise-account-settings-");
        try {
            Path path = directory.resolve("account-settings.csv");
            action.run(new CsvAccountPreferenceRepository(path), path);
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
        void run(CsvAccountPreferenceRepository repository, Path path)
                throws Exception;
    }
}
