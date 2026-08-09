package com.spendwise.app;

import com.spendwise.auth.OwnerConfiguration;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.admin.AdminService;
import com.spendwise.auth.audit.CsvAuditRepository;
import com.spendwise.auth.local.CsvLocalUserRepository;
import com.spendwise.auth.local.LegacyDataMigrationService;
import com.spendwise.auth.local.LocalDesktopAuthService;
import com.spendwise.auth.otp.HttpEmailVerificationGateway;
import com.spendwise.auth.otp.OtpRelayConfiguration;
import com.spendwise.auth.ui.AccountProfileDialog;
import com.spendwise.auth.ui.AdminConsoleDialog;
import com.spendwise.auth.ui.AuthFrame;
import com.spendwise.auth.ui.SecuritySessionsDialog;
import com.spendwise.config.AppPaths;
import com.spendwise.config.AppBrand;
import com.spendwise.config.LegacyAppDataImporter;
import com.spendwise.config.ProjectDataLock;
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
import com.spendwise.service.PresentationDataService;
import com.spendwise.service.PdfReportService;
import com.spendwise.ui.SpendWiseFrame;
import com.spendwise.voice.JavaSoundMicrophoneCapture;
import com.spendwise.voice.WindowsOfflineSpeechRecognitionProvider;
import com.spendwise.ui.component.ConfirmationDialogs;
import com.spendwise.ui.shell.ProfileMenuActions;
import com.spendwise.ui.theme.AppTheme;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class SpendWiseApplication {

    private static ProjectDataLock projectDataLock;

    private SpendWiseApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SpendWiseApplication::startApplication);
    }

    private static void startApplication() {
        AppTheme.initialize();
        try {
            projectDataLock = ProjectDataLock.acquire(
                    AppPaths.getDataRootDirectory());
            ProjectDataLock.ensureLayout(AppPaths.getDataRootDirectory());
            Runtime.getRuntime().addShutdownHook(new Thread(
                    SpendWiseApplication::releaseProjectDataLock,
                    "wealthora-data-lock-release"));
            offerLegacyAppDataImport();
            SessionManager sessionManager = new SessionManager();
            CsvAuditRepository auditRepository = new CsvAuditRepository(
                    AppPaths.getAuthenticationDirectory().resolve("audit.csv"));
            LocalDesktopAuthService authService =
                    new LocalDesktopAuthService(
                            new CsvLocalUserRepository(
                                    AppPaths.getAuthenticationDirectory()
                                            .resolve("users.csv")),
                            new PasswordService(),
                            OwnerConfiguration.fromEnvironment(),
                            sessionManager,
                            auditRepository,
                            new LegacyDataMigrationService(
                                    AppPaths.getLegacyDataDirectory(),
                                    AppPaths.getBackupDirectory(),
                                    auditRepository),
                            new HttpEmailVerificationGateway(
                                    OtpRelayConfiguration.fromEnvironment()));
            AdminService adminService = new AdminService(
                    authService.getUserRepository(), auditRepository,
                    authService);
            showAuthentication(authService, sessionManager, adminService);
        } catch (RuntimeException exception) {
            releaseProjectDataLock();
            showStartupError(exception);
        }
    }

    private static void offerLegacyAppDataImport() {
        LegacyAppDataImporter importer = new LegacyAppDataImporter(
                AppPaths.getDataRootDirectory(),
                AppPaths.getLegacyApplicationRoot());
        if (!importer.shouldOfferMigration()) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(null,
                "Wealthora found data from an older installation. Copy it "
                        + "into this project's portable data folder?\n\n"
                        + "The older files will not be changed or deleted.",
                "Import Older Wealthora Data",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            LegacyAppDataImporter.MigrationResult result =
                    importer.importLegacyData();
            JOptionPane.showMessageDialog(null,
                    "Imported " + result.copiedFileCount()
                            + " file(s) into the portable data folder.",
                    "Import Complete", JOptionPane.INFORMATION_MESSAGE);
        } else {
            importer.declineMigration();
        }
    }

    private static synchronized void releaseProjectDataLock() {
        if (projectDataLock != null) {
            projectDataLock.close();
            projectDataLock = null;
        }
    }

    private static void showAuthentication(
            LocalDesktopAuthService authService,
            SessionManager sessionManager,
            AdminService adminService) {
        AuthFrame authFrame = new AuthFrame(authService, sessionManager,
                session -> openFinanceWorkspace(session, authService,
                        sessionManager, adminService));
        authFrame.setVisible(true);
    }

    static void openFinanceWorkspace(
            UserSession session,
            LocalDesktopAuthService authService,
            SessionManager sessionManager,
            AdminService adminService) {
        try {
            sessionManager.startSession(session);
            AppPaths.activateUserDataDirectory(session.getUserIdentifier());
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
                    expenseService, incomeService, transferService,
                    recurringService);
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
                            new PdfReportService(),
                            new WindowsOfflineSpeechRecognitionProvider(
                                    new JavaSoundMicrophoneCapture()));
            if (session.isOwner()) {
                frame.configurePresentationData(new PresentationDataService(
                        accountService, expenseService, incomeService,
                        transferService,
                        AppPaths.getPresentationDirectory().resolve(
                                session.getUserIdentifier()
                                        + "-manifest.properties")));
            }
            frame.configureProfileMenu(session, new ProfileMenuActions(
                    frame::openMyFinance,
                    () -> new AccountProfileDialog(frame, session)
                            .setVisible(true),
                    () -> new SecuritySessionsDialog(
                            frame, session, authService,
                            () -> returnToAuthentication(
                                    frame, authService,
                                    sessionManager, adminService))
                            .setVisible(true),
                    () -> leaveWorkspace(frame, authService, sessionManager,
                            adminService, true),
                    session.canAccessAdminConsole()
                            ? () -> new AdminConsoleDialog(
                                    frame, adminService, session,
                                    frame::createFinanceBackup,
                                    frame::restoreFinanceBackup)
                                    .setVisible(true)
                            : null,
                    () -> leaveWorkspace(frame, authService, sessionManager,
                            adminService, false)));
            frame.setVisible(true);
        } catch (RuntimeException exception) {
            try {
                authService.logout();
            } catch (RuntimeException ignored) {
                sessionManager.clearSession();
                AppPaths.clearUserDataDirectory();
            }
            showStartupError(exception);
            showAuthentication(authService, sessionManager, adminService);
        }
    }

    private static void leaveWorkspace(
            SpendWiseFrame frame,
            LocalDesktopAuthService authService,
            SessionManager sessionManager,
            AdminService adminService,
            boolean switchingAccount) {
        String action = switchingAccount ? "Switch Account" : "Sign Out";
        String message = switchingAccount
                ? "Close My Finance and return to the account sign-in screen?"
                : "Sign out of Wealthora on this device?";
        if (!ConfirmationDialogs.confirm(
                frame, action, message, JOptionPane.QUESTION_MESSAGE)) {
            return;
        }
        for (java.awt.Window owned : frame.getOwnedWindows()) {
            owned.dispose();
        }
        frame.dispose();
        try {
            if (switchingAccount) {
                authService.switchAccount();
            } else {
                authService.logout();
            }
        } catch (RuntimeException exception) {
            sessionManager.clearSession();
            AppPaths.clearUserDataDirectory();
            ConfirmationDialogs.showError(
                    null, action + " audit warning", exception);
        }
        showAuthentication(authService, sessionManager, adminService);
    }

    private static void returnToAuthentication(
            SpendWiseFrame frame,
            LocalDesktopAuthService authService,
            SessionManager sessionManager,
            AdminService adminService) {
        for (java.awt.Window owned : frame.getOwnedWindows()) {
            owned.dispose();
        }
        frame.dispose();
        sessionManager.clearSession();
        AppPaths.clearUserDataDirectory();
        showAuthentication(authService, sessionManager, adminService);
    }

    private static void showStartupError(RuntimeException exception) {
        JOptionPane.showMessageDialog(
                null,
                startupErrorMessage(exception),
                AppBrand.APP_NAME + " Could Not Start",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String startupErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return AppBrand.APP_NAME + " could not start safely.";
        }
        return AppBrand.APP_NAME + " could not start: " + message;
    }
}
