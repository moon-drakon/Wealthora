package com.spendwise.app;

import com.spendwise.config.AppPaths;
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
import com.spendwise.ui.SpendWiseFrame;
import com.spendwise.ui.theme.AppTheme;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class SpendWiseApplication {

    private SpendWiseApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SpendWiseApplication::startApplication);
    }

    private static void startApplication() {
        AppTheme.initialize();
        try {
            Path categoryCsvPath = AppPaths.getCategoryCsvPath();
            CsvCategoryRepository categoryRepository =
                    new CsvCategoryRepository(categoryCsvPath);
            CategoryService categoryService =
                    new CategoryService(categoryRepository);
            CsvAccountRepository accountRepository =
                    new CsvAccountRepository(AppPaths.getAccountCsvPath());
            AccountService accountService =
                    new AccountService(
                            accountRepository,
                            new CsvAccountPreferenceRepository(
                                    AppPaths.getAccountSettingsCsvPath()));
            Path expenseCsvPath = AppPaths.getExpenseCsvPath();
            CsvExpenseRepository repository =
                    new CsvExpenseRepository(
                            expenseCsvPath,
                            categoryService::resolveCategory,
                            accountService::resolveAccount);
            ExpenseService expenseService =
                    new ExpenseService(repository, accountService);
            CsvIncomeRepository incomeRepository =
                    new CsvIncomeRepository(
                            AppPaths.getIncomeCsvPath(),
                            accountService::resolveAccount);
            IncomeService incomeService =
                    new IncomeService(incomeRepository, accountService);
            CsvTransferRepository transferRepository =
                    new CsvTransferRepository(
                            AppPaths.getTransferCsvPath(),
                            accountService::resolveAccount);
            TransferService transferService =
                    new TransferService(
                            transferRepository, accountService);
            FinanceService financeService =
                    new FinanceService(
                            accountService,
                            expenseService,
                            incomeService,
                            transferService);
            PaymentCardService paymentCardService = new PaymentCardService(
                    new CsvPaymentCardRepository(
                            AppPaths.getPaymentCardCsvPath(),
                            accountService::resolveAccount),
                    accountService);
            CurrencyService currencyService = new CurrencyService(
                    new CsvCurrencyPreferenceRepository(
                            AppPaths.getCurrencySettingsCsvPath()));
            ExpenseAnalyticsService analyticsService =
                    new ExpenseAnalyticsService(expenseService);
            Path budgetCsvPath = AppPaths.getBudgetCsvPath();
            CsvBudgetRepository budgetRepository =
                    new CsvBudgetRepository(
                            budgetCsvPath, categoryService::resolveCategory);
            BudgetService budgetService = new BudgetService(budgetRepository);
            AdvancedBudgetService advancedBudgetService =
                    new AdvancedBudgetService(new CsvBudgetPlanRepository(
                            AppPaths.getBudgetPlanCsvPath(),
                            categoryService::resolveCategory), expenseService);
            CsvRecurringEntryRepository recurringRepository =
                    new CsvRecurringEntryRepository(
                            AppPaths.getRecurringCsvPath(),
                            categoryService::resolveCategory,
                            accountService::resolveAccount);
            RecurringService recurringService = new RecurringService(
                    recurringRepository,
                    expenseService,
                    incomeService,
                    transferService,
                    accountService,
                    categoryService);
            QuickEntryService quickEntryService = new QuickEntryService(
                    expenseService, incomeService, transferService);
            BackupService backupService = new BackupService(
                    expenseCsvPath.getParent());
            ExportService exportService = new ExportService(
                    expenseService,
                    incomeService,
                    transferService,
                    accountService,
                    financeService);
            SavingsGoalService savingsGoalService = new SavingsGoalService(
                    new CsvSavingsGoalRepository(
                            AppPaths.getSavingsGoalCsvPath(),
                            accountService::resolveAccount), accountService);
            DebtService debtService = new DebtService(new CsvDebtRepository(
                    AppPaths.getDebtCsvPath()));
            FinancialReportingService reportingService =
                    new FinancialReportingService(expenseService, incomeService,
                            transferService, accountService, budgetService);
            PortfolioAnalyticsService portfolioService =
                    new PortfolioAnalyticsService(reportingService,
                            financeService, recurringService,
                            advancedBudgetService, debtService);
            FinanceNotificationService notificationService =
                    new FinanceNotificationService(recurringService,
                            paymentCardService, analyticsService, budgetService,
                            advancedBudgetService, debtService);
            Path dataDirectory = expenseCsvPath.getParent();
            JsonBackupService jsonBackupService =
                    new JsonBackupService(dataDirectory);
            CsvImportService csvImportService = new CsvImportService(
                    dataDirectory, backupService, expenseService, incomeService,
                    transferService, accountService, categoryService);
            SpendWiseFrame frame =
                    new SpendWiseFrame(
                            expenseService,
                            analyticsService,
                            budgetService,
                            categoryService,
                            accountService,
                            incomeService,
                            transferService,
                            financeService,
                            recurringService,
                            quickEntryService,
                            backupService,
                            exportService,
                            paymentCardService,
                            currencyService,
                            advancedBudgetService,
                            savingsGoalService,
                            debtService,
                            portfolioService,
                            notificationService,
                            jsonBackupService,
                            csvImportService,
                            new PdfReportService());
            frame.setVisible(true);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(
                    null,
                    startupErrorMessage(exception),
                    AppBrand.APP_NAME + " Could Not Start",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String startupErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return AppBrand.APP_NAME + " could not start safely.";
        }
        return AppBrand.APP_NAME + " could not start: " + message;
    }
}
