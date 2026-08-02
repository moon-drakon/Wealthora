package com.spendwise.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FinanceAppPathsTest {

    private static int passed;

    private FinanceAppPathsTest() {
    }

    public static void main(String[] args) {
        test("Windows finance paths", FinanceAppPathsTest::windowsPaths);
        test("Linux finance paths", FinanceAppPathsTest::linuxPaths);
        test("current finance paths are siblings", FinanceAppPathsTest::currentPaths);
        System.out.println(
                "All " + passed + " finance AppPaths tests passed.");
    }

    private static void windowsPaths() {
        Path root = Path.of("C:\\Data");
        assertEquals(
                root.resolve("SpendWiseExpenseTracker")
                        .resolve("data")
                        .resolve("accounts.csv")
                        .toAbsolutePath()
                        .normalize(),
                AppPaths.resolveAccountCsvPath(
                        "Windows 11", root.toString(), null, "C:\\Home"));
        assertEquals("income.csv",
                AppPaths.resolveIncomeCsvPath(
                        "Windows 11", root.toString(), null, "C:\\Home")
                        .getFileName().toString());
        assertEquals("transfers.csv",
                AppPaths.resolveTransferCsvPath(
                        "Windows 11", root.toString(), null, "C:\\Home")
                        .getFileName().toString());
        assertEquals("recurring.csv",
                AppPaths.resolveRecurringCsvPath(
                        "Windows 11", root.toString(), null, "C:\\Home")
                        .getFileName().toString());
        assertEquals("account-settings.csv",
                AppPaths.resolveAccountSettingsCsvPath(
                        "Windows 11", root.toString(), null, "C:\\Home")
                        .getFileName().toString());
    }

    private static void linuxPaths() {
        Path root = Path.of("build", "finance-xdg");
        Path accountPath = AppPaths.resolveAccountCsvPath(
                "Linux", null, root.toString(), "/tmp/home");
        assertEquals(
                root.resolve("SpendWiseExpenseTracker")
                        .resolve("data")
                        .resolve("accounts.csv")
                        .toAbsolutePath()
                        .normalize(),
                accountPath);
    }

    private static void currentPaths() {
        Path account = AppPaths.getAccountCsvPath();
        Path income = AppPaths.getIncomeCsvPath();
        Path transfers = AppPaths.getTransferCsvPath();
        Path recurring = AppPaths.getRecurringCsvPath();
        Path accountSettings = AppPaths.getAccountSettingsCsvPath();
        Path budgetPlans = AppPaths.getBudgetPlanCsvPath();
        Path savingsGoals = AppPaths.getSavingsGoalCsvPath();
        Path debts = AppPaths.getDebtCsvPath();
        assertEquals(account.getParent(), income.getParent());
        assertEquals(account.getParent(), transfers.getParent());
        assertEquals(account.getParent(), recurring.getParent());
        assertEquals(account.getParent(), accountSettings.getParent());
        assertEquals(account.getParent(), budgetPlans.getParent());
        assertEquals(account.getParent(), savingsGoals.getParent());
        assertEquals(account.getParent(), debts.getParent());
        assertFalse(Files.exists(account) && Files.isDirectory(account));
        assertFalse(Files.exists(income) && Files.isDirectory(income));
        assertFalse(Files.exists(transfers) && Files.isDirectory(transfers));
        assertFalse(Files.exists(recurring) && Files.isDirectory(recurring));
        assertFalse(Files.exists(accountSettings)
                && Files.isDirectory(accountSettings));
        assertFalse(Files.exists(budgetPlans) && Files.isDirectory(budgetPlans));
        assertFalse(Files.exists(savingsGoals) && Files.isDirectory(savingsGoals));
        assertFalse(Files.exists(debts) && Files.isDirectory(debts));
    }

    private static void test(String name, Runnable test) {
        try {
            test.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }
}
