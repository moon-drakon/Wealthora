package com.spendwise.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.repository.CsvAccountRepository;
import com.spendwise.repository.CsvAccountPreferenceRepository;
import com.spendwise.repository.CsvBudgetRepository;
import com.spendwise.repository.CsvCategoryRepository;
import com.spendwise.repository.CsvExpenseRepository;
import com.spendwise.repository.CsvIncomeRepository;
import com.spendwise.repository.CsvRecurringEntryRepository;
import com.spendwise.repository.CsvTransferRepository;
import com.spendwise.repository.CsvPaymentCardRepository;
import com.spendwise.repository.CsvCurrencyPreferenceRepository;
import com.spendwise.repository.CsvBudgetPlanRepository;
import com.spendwise.repository.CsvSavingsGoalRepository;
import com.spendwise.repository.CsvDebtRepository;
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
import com.spendwise.service.PaymentCardService;
import com.spendwise.service.CurrencyService;
import com.spendwise.service.AdvancedBudgetService;
import com.spendwise.service.SavingsGoalService;
import com.spendwise.service.DebtService;
import com.spendwise.service.FinancialReportingService;
import com.spendwise.service.PortfolioAnalyticsService;
import com.spendwise.service.FinanceNotificationService;
import com.spendwise.service.JsonBackupService;
import com.spendwise.service.CsvImportService;
import com.spendwise.service.PdfReportService;
import com.spendwise.ui.shell.AppShellPanel;
import com.spendwise.voice.JavaSoundMicrophoneCapture;
import com.spendwise.voice.WindowsOfflineSpeechRecognitionProvider;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
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
                FinanceService finance = new FinanceService(
                        accounts, expenses, income, transfers);
                PaymentCardService cards = new PaymentCardService(
                        new CsvPaymentCardRepository(
                                directory.resolve("cards.csv"),
                                accounts::resolveAccount), accounts);
                CurrencyService currency = new CurrencyService(
                        new CsvCurrencyPreferenceRepository(
                                directory.resolve("currency-settings.csv")));
                AdvancedBudgetService advancedBudgets =
                        new AdvancedBudgetService(
                                new CsvBudgetPlanRepository(
                                    directory.resolve("budget-plans.csv"),
                                    categories::resolveCategory), expenses);
                SavingsGoalService goals = new SavingsGoalService(
                        new CsvSavingsGoalRepository(
                                directory.resolve("savings-goals.csv"),
                                accounts::resolveAccount), accounts);
                DebtService debts = new DebtService(new CsvDebtRepository(
                        directory.resolve("debts.csv")));
                FinancialReportingService reporting =
                        new FinancialReportingService(expenses, income,
                                transfers, accounts, budgets);
                PortfolioAnalyticsService portfolio =
                        new PortfolioAnalyticsService(reporting, finance,
                                recurring, advancedBudgets, debts);
                BackupService backups = new BackupService(directory);
                frame = new SpendWiseFrame(
                        expenses,
                        analytics,
                        budgets,
                        categories,
                        accounts,
                        income,
                        transfers,
                        finance,
                        recurring,
                        new QuickEntryService(expenses, income, transfers),
                        backups,
                        new ExportService(
                                expenses,
                                income,
                                transfers,
                                accounts,
                                finance),
                        cards,
                        currency,
                        advancedBudgets,
                        goals,
                        debts,
                        portfolio,
                        new FinanceNotificationService(recurring, cards,
                                analytics, budgets, advancedBudgets, debts),
                        new JsonBackupService(directory),
                        new CsvImportService(directory, backups, expenses,
                                income, transfers, accounts, categories),
                        new PdfReportService(),
                        new WindowsOfflineSpeechRecognitionProvider(
                                new JavaSoundMicrophoneCapture()));
                frame.setLocation(-10000, -10000);
                frame.setSize(1000, 650);
                frame.setVisible(true);
                assertEquals(AppBrand.WINDOW_TITLE, frame.getTitle());
                if (!(frame.getContentPane() instanceof AppShellPanel shell)) {
                    throw new AssertionError(
                            "Main content must use the professional app shell.");
                }
                assertEquals(14, shell.getPageCount());
                assertEquals("dashboard", shell.getCurrentPage());
                for (String page : new String[] {
                    "dashboard", "transactions", "accounts", "budgets",
                    "cards", "calendar", "recurring", "reports", "goals",
                    "loans", "settings", "expenses", "notifications",
                    "analytics"
                }) {
                    shell.showPage(page);
                    assertEquals(page, shell.getCurrentPage());
                }
                frame.setSize(980, 640);
                frame.validate();
                frame.setSize(1440, 900);
                frame.validate();
                shell.setDarkMode(true);
                shell.setDarkMode(false);
                if (frame.getJMenuBar() == null
                        || frame.getJMenuBar().getMenuCount() != 2) {
                    throw new AssertionError(
                            "Quick Entry and Data menus must be available.");
                }
                assertEquals("Entry",
                        frame.getJMenuBar().getMenu(0).getText());
                assertEquals("Voice Quick Entry",
                        frame.getJMenuBar().getMenu(0).getItem(1).getText());
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
