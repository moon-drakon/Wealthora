package com.spendwise.ui;

import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvAccountPreferenceRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.service.AccountService;
import com.spendwise.service.BackupService;
import com.spendwise.service.BudgetService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.ExpenseAnalyticsService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.ExportService;
import com.spendwise.service.FinanceService;
import com.spendwise.service.IncomeService;
import com.spendwise.service.QuickEntryService;
import com.spendwise.service.RecurringService;
import com.spendwise.service.TransferService;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public final class FinanceFrameSmokeTest {

    private FinanceFrameSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new AssertionError(
                    "The isolated finance frame smoke test requires a display.");
        }
        Path directory = Files.createTempDirectory(
                "spendwise-finance-gui-smoke-");
        try {
            runOnEventDispatchThread(directory);
            assertDirectoryRemainsEmpty(directory);
            System.out.println(
                    "Finance frame GUI smoke test passed with isolated data.");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void runOnEventDispatchThread(Path directory)
            throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            SpendWiseFrame frame = null;
            try {
                CategoryService categories = new CategoryService(
                        new CsvCategoryRepository(
                                directory.resolve("categories.csv")));
                AccountService accounts = new AccountService(
                        new CsvAccountRepository(
                                directory.resolve("accounts.csv")),
                        new CsvAccountPreferenceRepository(
                                directory.resolve("account-settings.csv")));
                ExpenseService expenses = new ExpenseService(
                        new CsvExpenseRepository(
                                directory.resolve("expenses.csv"),
                                categories::resolveCategory,
                                accounts::resolveAccount),
                        accounts);
                IncomeService income = new IncomeService(
                        new CsvIncomeRepository(
                                directory.resolve("income.csv"),
                                accounts::resolveAccount),
                        accounts);
                TransferService transfers = new TransferService(
                        new CsvTransferRepository(
                                directory.resolve("transfers.csv"),
                                accounts::resolveAccount),
                        accounts);
                BudgetService budgets = new BudgetService(
                        new CsvBudgetRepository(
                                directory.resolve("budgets.csv"),
                                categories::resolveCategory));
                ExpenseAnalyticsService analytics =
                        new ExpenseAnalyticsService(expenses);
                RecurringService recurring = new RecurringService(
                        new CsvRecurringEntryRepository(
                                directory.resolve("recurring.csv"),
                                categories::resolveCategory,
                                accounts::resolveAccount),
                        expenses,
                        income,
                        transfers,
                        accounts,
                        categories);
                frame = new SpendWiseFrame(
                        expenses,
                        analytics,
                        budgets,
                        categories,
                        accounts,
                        income,
                        transfers,
                        new FinanceService(
                                accounts, expenses, income, transfers),
                        recurring,
                        new QuickEntryService(expenses, income, transfers),
                        new BackupService(directory),
                        new ExportService(
                                expenses,
                                income,
                                transfers,
                                accounts,
                                new FinanceService(
                                    accounts, expenses, income, transfers)));
                frame.setLocation(-10000, -10000);
                frame.setSize(1000, 650);
                frame.setVisible(true);
                if (!(frame.getContentPane() instanceof JTabbedPane tabs)) {
                    throw new AssertionError(
                            "Main content must be a tabbed pane.");
                }
                assertEquals(7, tabs.getTabCount());
                assertEquals("Expenses", tabs.getTitleAt(0));
                assertEquals("Finance", tabs.getTitleAt(3));
                assertEquals("Calendar", tabs.getTitleAt(4));
                assertEquals("Reports", tabs.getTitleAt(5));
                assertEquals("Recurring", tabs.getTitleAt(6));
                tabs.setSelectedIndex(4);
                tabs.setSelectedIndex(5);
                tabs.setSelectedIndex(6);
                tabs.setSelectedIndex(0);
                if (frame.getJMenuBar() == null
                        || frame.getJMenuBar().getMenuCount() != 2) {
                    throw new AssertionError(
                            "Quick Entry and Data menus must be available.");
                }
                assertEquals("Data", frame.getJMenuBar().getMenu(1).getText());
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(
                    "Finance frame GUI smoke test failed.", failure.get());
        }
    }

    private static void assertDirectoryRemainsEmpty(Path directory)
            throws IOException {
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isPresent()) {
                throw new AssertionError(
                        "Viewing the UI created an isolated CSV file.");
            }
        }
    }

    private static void deleteRecursively(Path directory)
            throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }
}
