package com.spendwise.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public class AppPathsTest {

    private static int passedTests;

    public static void main(String[] args) {
        runTest("Windows LOCALAPPDATA path", AppPathsTest::windowsLocalAppDataPath);
        runTest("Windows home fallback", AppPathsTest::windowsHomeFallback);
        runTest("blank Windows LOCALAPPDATA", AppPathsTest::blankLocalAppDataUsesFallback);
        runTest("Linux XDG_DATA_HOME path", AppPathsTest::linuxXdgDataHomePath);
        runTest("Linux home fallback", AppPathsTest::linuxHomeFallback);
        runTest("macOS Application Support path", AppPathsTest::macOsPath);
        runTest("normalized path", AppPathsTest::pathIsNormalized);
        runTest("absolute path", AppPathsTest::pathIsAbsolute);
        runTest("application data suffix", AppPathsTest::pathHasExpectedSuffix);
        runTest("resolution has no file effects", AppPathsTest::resolutionCreatesNothing);
        runTest("missing home rejection", AppPathsTest::missingHomeIsRejected);
        runTest("caller-provided roots", AppPathsTest::resolutionUsesCallerProvidedRoots);
        runTest("budget sibling path", AppPathsTest::budgetPathIsExpenseSibling);
        runTest("budget filename", AppPathsTest::budgetPathHasExpectedSuffix);
        runTest("budget resolution side effects", AppPathsTest::budgetResolutionCreatesNothing);

        System.out.println("All " + passedTests + " AppPaths tests passed.");
    }

    private static void windowsLocalAppDataPath() {
        Path root = syntheticRoot("windows-local");
        Path localAppData = root.resolve("LocalAppData");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Windows 11",
                "  " + localAppData + "  ",
                null,
                root.resolve("Home").toString());

        assertEquals(expectedExpensePath(localAppData), actual,
                "Windows should prefer LOCALAPPDATA.");
    }

    private static void windowsHomeFallback() {
        Path home = syntheticRoot("windows-home");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Windows 11", null, null, "  " + home + "  ");

        assertEquals(
                expectedExpensePath(home.resolve("AppData").resolve("Local")),
                actual,
                "Windows home fallback is incorrect.");
    }

    private static void blankLocalAppDataUsesFallback() {
        Path home = syntheticRoot("windows-blank-local");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Windows 10", "   ", null, home.toString());

        assertEquals(
                expectedExpensePath(home.resolve("AppData").resolve("Local")),
                actual,
                "Blank LOCALAPPDATA should use the home fallback.");
    }

    private static void linuxXdgDataHomePath() {
        Path xdgDataHome = syntheticRoot("linux-xdg");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Linux", null, xdgDataHome.toString(), null);

        assertEquals(expectedExpensePath(xdgDataHome), actual,
                "Linux should prefer XDG_DATA_HOME.");
    }

    private static void linuxHomeFallback() {
        Path home = syntheticRoot("linux-home");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Linux", null, "  ", home.toString());

        assertEquals(
                expectedExpensePath(home.resolve(".local").resolve("share")),
                actual,
                "Linux home fallback is incorrect.");
    }

    private static void macOsPath() {
        Path home = syntheticRoot("mac-home");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Mac OS X", null, null, home.toString());

        assertEquals(
                expectedExpensePath(
                        home.resolve("Library").resolve("Application Support")),
                actual,
                "macOS Application Support path is incorrect.");
    }

    private static void pathIsNormalized() {
        Path root = syntheticRoot("normalized");
        Path unnormalized = root.resolve("first").resolve("..").resolve("data-root");

        Path actual = AppPaths.resolveExpenseCsvPath(
                "Linux", null, unnormalized.toString(), null);

        assertEquals(expectedExpensePath(root.resolve("data-root")), actual,
                "Resolved path should be normalized.");
        assertFalse(actual.toString().contains(".."),
                "Normalized path should not retain parent segments.");
    }

    private static void pathIsAbsolute() {
        Path actual = AppPaths.resolveExpenseCsvPath(
                "Linux", null, "relative-data-root", null);

        assertTrue(actual.isAbsolute(), "Resolved path should be absolute.");
    }

    private static void pathHasExpectedSuffix() {
        Path actual = AppPaths.resolveExpenseCsvPath(
                "Linux", null, syntheticRoot("suffix").toString(), null);

        assertTrue(
                actual.endsWith(Path.of(
                        "SpendWiseExpenseTracker", "data", "expenses.csv")),
                "Resolved path has the wrong application-data suffix.");
    }

    private static void resolutionCreatesNothing() {
        Path root = syntheticRoot("no-side-effects");
        assertFalse(Files.exists(root), "Synthetic test root should start missing.");

        Path resolved = AppPaths.resolveExpenseCsvPath(
                "Windows 11", root.resolve("local").toString(), null, null);

        assertFalse(Files.exists(root), "Path resolution created a directory.");
        assertFalse(Files.exists(resolved), "Path resolution created the CSV file.");
    }

    private static void missingHomeIsRejected() {
        expectThrows(
                IllegalStateException.class,
                () -> AppPaths.resolveExpenseCsvPath(
                        "Windows 11", " ", null, null),
                "Windows fallback should require user.home.");
        expectThrows(
                IllegalStateException.class,
                () -> AppPaths.resolveExpenseCsvPath(
                        "Mac OS X", null, null, " "),
                "macOS should require user.home.");
        expectThrows(
                IllegalStateException.class,
                () -> AppPaths.resolveExpenseCsvPath(
                        "Linux", null, null, null),
                "Linux fallback should require user.home.");
    }

    private static void resolutionUsesCallerProvidedRoots() {
        Path firstHome = syntheticRoot("user-one");
        Path secondHome = syntheticRoot("user-two");

        Path first = AppPaths.resolveExpenseCsvPath(
                "Linux", null, null, firstHome.toString());
        Path second = AppPaths.resolveExpenseCsvPath(
                "Linux", null, null, secondHome.toString());

        assertTrue(first.startsWith(firstHome), "First user root was not honored.");
        assertTrue(second.startsWith(secondHome), "Second user root was not honored.");
        assertFalse(first.equals(second), "Resolution should not use one fixed machine path.");
    }

    private static void budgetPathIsExpenseSibling() {
        Path root = syntheticRoot("budget-sibling");
        Path expensePath = AppPaths.resolveExpenseCsvPath(
                "Windows 11", root.toString(), null, null);
        Path budgetPath = AppPaths.resolveBudgetCsvPath(
                "Windows 11", root.toString(), null, null);

        assertEquals(expensePath.getParent(), budgetPath.getParent(),
                "Budget and expense CSV files should be siblings.");
    }

    private static void budgetPathHasExpectedSuffix() {
        Path actual = AppPaths.resolveBudgetCsvPath(
                "Linux", null, syntheticRoot("budget-suffix").toString(), null);

        assertTrue(
                actual.endsWith(Path.of(
                        "SpendWiseExpenseTracker", "data", "budgets.csv")),
                "Budget path has the wrong application-data suffix.");
    }

    private static void budgetResolutionCreatesNothing() {
        Path root = syntheticRoot("budget-no-side-effects");
        Path resolved = AppPaths.resolveBudgetCsvPath(
                "Windows 11", root.resolve("local").toString(), null, null);

        assertFalse(Files.exists(root), "Budget path resolution created a directory.");
        assertFalse(Files.exists(resolved), "Budget path resolution created the CSV file.");
    }

    private static Path expectedExpensePath(Path dataRoot) {
        return dataRoot
                .resolve("SpendWiseExpenseTracker")
                .resolve("data")
                .resolve("expenses.csv")
                .toAbsolutePath()
                .normalize();
    }

    private static Path syntheticRoot(String name) {
        return Path.of(
                System.getProperty("java.io.tmpdir"),
                "spendwise-app-path-tests",
                name + "-" + UUID.randomUUID())
                .toAbsolutePath()
                .normalize();
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError("AppPaths test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> void expectThrows(
            Class<T> expectedType, TestCase action, String message) {
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
}
