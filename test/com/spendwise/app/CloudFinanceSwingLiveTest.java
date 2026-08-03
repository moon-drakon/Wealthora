package com.spendwise.app;

import com.spendwise.auth.CloudConnectionState;
import com.spendwise.auth.FinanceMode;
import com.spendwise.auth.OwnerConfiguration;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.admin.AdminService;
import com.spendwise.auth.audit.CsvAuditRepository;
import com.spendwise.auth.local.CsvLocalUserRepository;
import com.spendwise.auth.local.LegacyDataMigrationService;
import com.spendwise.auth.local.LocalDesktopAuthService;
import com.spendwise.auth.registration.HttpRegistrationGateway;
import com.spendwise.auth.registration.ServerConfiguration;
import com.spendwise.ui.SpendWiseFrame;
import com.spendwise.ui.theme.AppTheme;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Opens the real Swing CLOUD workspace with disposable live test data.
 *
 * <p>This test intentionally keeps credentials and session tokens in memory.
 * The fixture marker contains only synthetic email addresses so a separate
 * server-side cleanup process can remove all generated database rows.</p>
 */
public final class CloudFinanceSwingLiveTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String EMAIL_PREFIX = "wealthora.swing.e2e.";

    private CloudFinanceSwingLiveTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new AssertionError(
                    "The live CLOUD Swing test requires a graphical desktop.");
        }

        Path repositoryRoot = requiredDirectory(
                "WEALTHORA_REPOSITORY_ROOT");
        Path mailDirectory = requiredExternalDirectory(
                "WEALTHORA_DEV_MAIL_DIR", repositoryRoot);
        Path fixtureFile = requiredExternalPath(
                "WEALTHORA_LIVE_FIXTURE_FILE", repositoryRoot);
        Path controlDirectory = fixtureFile.resolveSibling("control");
        deleteRecursively(controlDirectory);
        Files.createDirectories(controlDirectory);
        Path localDirectory = Files.createTempDirectory(
                "wealthora-cloud-swing-");
        char[] primaryPassword = randomPassword();
        char[] secondaryPassword = randomPassword();
        String marker = UUID.randomUUID().toString().replace("-", "");
        String primaryEmail = EMAIL_PREFIX + marker + "@northsouth.edu";
        String secondaryEmail = EMAIL_PREFIX + marker
                + ".isolated@northsouth.edu";
        Path primaryMail = verificationFile(mailDirectory, primaryEmail);
        Path secondaryMail = verificationFile(mailDirectory, secondaryEmail);
        HttpRegistrationGateway activeGateway = null;

        try {
            Files.writeString(fixtureFile, primaryEmail + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            HttpRegistrationGateway primaryGateway = gateway();
            provision(primaryGateway, primaryEmail, primaryPassword,
                    primaryMail, marker.substring(0, 12));
            UserSession primarySession = primaryGateway.signIn(
                    primaryEmail, primaryPassword);
            require(primarySession.getFinanceMode() == FinanceMode.CLOUD,
                    "The fixture did not receive a CLOUD session.");
            require(!primarySession.canAccessAdminConsole(),
                    "A normal CLOUD user received administration access.");
            seedFinanceData(primaryGateway);
            verifyFinanceCoverage(primaryGateway);
            System.out.println("CloudFixtureProvisioned: PASS");
            System.out.println("CloudFinanceCoverage: PASS");
            System.out.println("CloudUserRestriction: PASS");

            Files.writeString(fixtureFile,
                    secondaryEmail + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            HttpRegistrationGateway secondaryGateway = gateway();
            provision(secondaryGateway, secondaryEmail, secondaryPassword,
                    secondaryMail, marker.substring(0, 12) + "i");
            secondaryGateway.signIn(secondaryEmail, secondaryPassword);
            String isolatedAccounts = secondaryGateway.requestFinance(
                    "GET", "/api/finance/accounts?page=0&size=100", "");
            String isolatedTransactions = secondaryGateway.requestFinance(
                    "GET", "/api/finance/transactions?page=0&size=100", "");
            require(!isolatedAccounts.contains("ACCOUNT_CLOUD_SMOKE_MAIN")
                    && !isolatedTransactions.contains(
                            "EXPENSE_CLOUD_SMOKE"),
                    "The second user could see the primary user's data.");
            secondaryGateway.logout();
            require(!secondaryGateway.hasActiveSession(),
                    "Logout retained the secondary CLOUD session.");
            System.out.println("SecondUserIsolation: PASS");

            primaryGateway.logout();
            require(!primaryGateway.hasActiveSession(),
                    "Logout retained the primary CLOUD session.");
            activeGateway = gateway();
            UserSession restoredSession = activeGateway.signIn(
                    primaryEmail, primaryPassword);
            String restoredTransactions = activeGateway.requestFinance(
                    "GET", "/api/finance/transactions?page=0&size=100", "");
            require(restoredTransactions.contains("EXPENSE_CLOUD_SMOKE")
                    && restoredTransactions.contains("INCOME_CLOUD_SMOKE")
                    && restoredTransactions.contains(
                            "TRANSFER_CLOUD_SMOKE"),
                    "CLOUD records did not survive a fresh desktop session.");
            System.out.println("CloudReloginPersistence: PASS");
            System.out.println("CloudLogoutClearing: PASS");

            LocalDesktopAuthService authService = localAuthService(
                    localDirectory, activeGateway);
            SessionManager sessionManager = new SessionManager();
            CsvAuditRepository auditRepository = new CsvAuditRepository(
                    localDirectory.resolve("audit.csv"));
            AdminService adminService = new AdminService(
                    authService.getUserRepository(), auditRepository,
                    authService);
            HttpRegistrationGateway shutdownGateway = activeGateway;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    shutdownGateway.logout();
                } catch (RuntimeException ignored) {
                    // Fixture cleanup must continue when the server is offline.
                }
                clearAndDelete(primaryMail);
                clearAndDelete(secondaryMail);
                deleteRecursively(localDirectory);
                deleteRecursively(controlDirectory);
                Arrays.fill(primaryPassword, '\0');
                Arrays.fill(secondaryPassword, '\0');
            }, "wealthora-cloud-fixture-cleanup"));

            openWorkspace(restoredSession, authService, sessionManager,
                    adminService);
            System.out.println("CloudSwingReady: PASS");
            verifyServerRestart(activeGateway, controlDirectory);
        } catch (Throwable failure) {
            if (activeGateway != null) {
                try {
                    activeGateway.logout();
                } catch (RuntimeException ignored) {
                    // Preserve the original failure.
                }
            }
            clearAndDelete(primaryMail);
            clearAndDelete(secondaryMail);
            deleteRecursively(localDirectory);
            deleteRecursively(controlDirectory);
            Arrays.fill(primaryPassword, '\0');
            Arrays.fill(secondaryPassword, '\0');
            System.err.println("Live CLOUD Swing test failed. Type="
                    + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private static void provision(HttpRegistrationGateway gateway,
            String email, char[] password, Path verificationFile,
            String studentIdentifier) throws Exception {
        gateway.register("Wealthora CLOUD Test Student", email,
                studentIdentifier, password, password, true);
        String code = secretLine(awaitFile(verificationFile), "code=");
        gateway.verifyEmail(email, code);
    }

    private static void seedFinanceData(HttpRegistrationGateway gateway) {
        LocalDate today = LocalDate.now();
        String date = today.toString();
        String month = YearMonth.from(today).toString();
        gateway.requestFinance("POST", "/api/finance/accounts", """
                {"externalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "name":"University Wallet",
                "accountType":"BANK","currencyCode":"BDT",
                "openingBalance":5000,"iconName":"bank",
                "colorHex":"#1F7E60","institutionName":"Test Bank",
                "openedOn":"%s","archived":false}
                """.formatted(date));
        gateway.requestFinance("POST", "/api/finance/accounts", """
                {"externalId":"ACCOUNT_CLOUD_SMOKE_SAVINGS",
                "name":"Semester Savings",
                "accountType":"SAVINGS","currencyCode":"BDT",
                "openingBalance":2000,"iconName":"savings",
                "colorHex":"#245B78","institutionName":"Test Bank",
                "openedOn":"%s","archived":false}
                """.formatted(date));
        gateway.requestFinance("POST", "/api/finance/categories", """
                {"externalId":"CUSTOM_CLOUD_SMOKE_STUDY",
                "name":"Study Materials",
                "parentExternalId":"EDUCATION","archived":false}
                """);
        gateway.requestFinance("POST", "/api/finance/income", """
                {"externalId":"INCOME_CLOUD_SMOKE",
                "source":"Study Stipend",
                "amount":12500,"date":"%s",
                "accountExternalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "paymentMethod":"BANK_TRANSFER","tags":["study"],
                "note":"Disposable live test record"}
                """.formatted(date));
        gateway.requestFinance("POST", "/api/finance/expenses", """
                {"externalId":"EXPENSE_CLOUD_SMOKE",
                "description":"Course Materials","amount":1500,
                "date":"%s",
                "accountExternalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "categoryExternalId":"CUSTOM_CLOUD_SMOKE_STUDY",
                "paymentMethod":"CREDIT_CARD","tags":["semester"],
                "note":"Disposable live test record"}
                """.formatted(date));
        gateway.requestFinance("POST", "/api/finance/expenses", """
                {"externalId":"EXPENSE_CLOUD_DELETE",
                "description":"Temporary Draft","amount":100,
                "date":"%s",
                "accountExternalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "categoryExternalId":"CUSTOM_CLOUD_SMOKE_STUDY",
                "paymentMethod":"CASH","tags":["temporary"],
                "note":"Disposable edit and delete check"}
                """.formatted(date));
        gateway.requestFinance("PUT",
                "/api/finance/expenses/EXPENSE_CLOUD_DELETE", """
                {"externalId":"EXPENSE_CLOUD_DELETE",
                "description":"Updated Temporary Draft","amount":200,
                "date":"%s",
                "accountExternalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "categoryExternalId":"CUSTOM_CLOUD_SMOKE_STUDY",
                "paymentMethod":"DEBIT_CARD","tags":["updated"],
                "note":"Disposable edit and delete check"}
                """.formatted(date));
        String editedTransactions = gateway.requestFinance(
                "GET", "/api/finance/transactions?page=0&size=100", "");
        require(editedTransactions.contains("Updated Temporary Draft")
                        && editedTransactions.contains("200.00"),
                "The transaction update did not persist.");
        gateway.requestFinance("DELETE",
                "/api/finance/expenses/EXPENSE_CLOUD_DELETE", "");
        gateway.requestFinance("POST", "/api/finance/transfers", """
                {"externalId":"TRANSFER_CLOUD_SMOKE","amount":1000,
                "date":"%s",
                "sourceAccountExternalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "destinationAccountExternalId":
                "ACCOUNT_CLOUD_SMOKE_SAVINGS",
                "tags":["reserve"],"note":"Disposable live test record"}
                """.formatted(date));
        gateway.requestFinance("PUT",
                "/api/finance/budgets/monthly/" + month, """
                {"month":"%s","overallLimit":10000,
                "categoryLimits":{"CUSTOM_CLOUD_SMOKE_STUDY":3000}}
                """.formatted(month));
        gateway.requestFinance("POST", "/api/finance/recurring", """
                {"externalId":"RECURRING_CLOUD_SMOKE",
                "entryType":"EXPENSE","amount":750,
                "description":"Monthly Study Plan",
                "categoryExternalId":"CUSTOM_CLOUD_SMOKE_STUDY",
                "sourceAccountExternalId":"ACCOUNT_CLOUD_SMOKE_MAIN",
                "destinationAccountExternalId":null,
                "frequency":"MONTHLY","interval":1,
                "startDate":"%s","endDate":null,
                "nextDueDate":"%s","recurringKind":"SUBSCRIPTION",
                "reminderDays":3,"active":true}
                """.formatted(date, today.plusMonths(1)));
        gateway.requestFinance("POST", "/api/finance/goals", """
                {"externalId":"GOAL_CLOUD_SMOKE",
                "name":"Semester Reserve","targetAmount":25000,
                "targetDate":"%s",
                "linkedAccountExternalId":"ACCOUNT_CLOUD_SMOKE_SAVINGS",
                "active":true}
                """.formatted(today.plusMonths(6)));
        gateway.requestFinance("POST", "/api/finance/debts", """
                {"externalId":"DEBT_CLOUD_SMOKE",
                "direction":"BORROWED","counterparty":"Test Lender",
                "originalAmount":3000,"dueDate":"%s",
                "note":"Disposable live test record"}
                """.formatted(today.plusMonths(3)));
    }

    private static void verifyFinanceCoverage(
            HttpRegistrationGateway gateway) {
        String transactions = gateway.requestFinance(
                "GET", "/api/finance/transactions?page=0&size=100", "");
        require(transactions.contains("EXPENSE_CLOUD_SMOKE")
                        && transactions.contains("INCOME_CLOUD_SMOKE")
                        && transactions.contains("TRANSFER_CLOUD_SMOKE")
                        && !transactions.contains("EXPENSE_CLOUD_DELETE"),
                "Transaction create, edit, or delete coverage failed.");
        require(gateway.requestFinance("GET",
                "/api/finance/budgets/monthly?page=0&size=100", "")
                .contains("10000.00"),
                "The monthly budget was not available.");
        require(gateway.requestFinance("GET",
                "/api/finance/recurring?page=0&size=100", "")
                .contains("RECURRING_CLOUD_SMOKE"),
                "The recurring entry was not available.");
        require(gateway.requestFinance("GET",
                "/api/finance/goals?page=0&size=100", "")
                .contains("GOAL_CLOUD_SMOKE"),
                "The savings goal was not available.");
        require(gateway.requestFinance("GET",
                "/api/finance/debts?page=0&size=100", "")
                .contains("DEBT_CLOUD_SMOKE"),
                "The debt record was not available.");
        String dashboard = gateway.requestFinance("GET",
                "/api/finance/reports/dashboard?month="
                        + YearMonth.now(), "");
        require(dashboard.contains("\"budgetLimit\":10000.00")
                        && dashboard.contains("\"expenses\":1500.00")
                        && dashboard.contains("\"income\":12500.00"),
                "The CLOUD dashboard summary was inconsistent.");
    }

    private static LocalDesktopAuthService localAuthService(
            Path directory, HttpRegistrationGateway gateway) {
        CsvAuditRepository auditRepository = new CsvAuditRepository(
                directory.resolve("audit.csv"));
        return new LocalDesktopAuthService(
                new CsvLocalUserRepository(directory.resolve("users.csv")),
                new PasswordService(), new OwnerConfiguration(""),
                new SessionManager(), auditRepository,
                new LegacyDataMigrationService(
                        directory.resolve("legacy"),
                        directory.resolve("backups"), auditRepository),
                gateway);
    }

    private static void openWorkspace(UserSession session,
            LocalDesktopAuthService authService,
            SessionManager sessionManager, AdminService adminService)
            throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppTheme.initialize();
                SpendWiseApplication.openFinanceWorkspace(session,
                        authService, sessionManager, adminService);
                SpendWiseFrame frame = Arrays.stream(Window.getWindows())
                        .filter(window -> window instanceof SpendWiseFrame
                                && window.isVisible())
                        .map(window -> (SpendWiseFrame) window)
                        .findFirst().orElse(null);
                require(frame != null,
                        "The real Wealthora finance frame was not visible.");
                require(hasLabel(frame, "Private CLOUD workspace"),
                        "The dashboard did not identify the CLOUD workspace.");
                require(!hasLabel(frame, "Stored locally. No account or cloud "
                                + "connection is active."),
                        "The CLOUD dashboard displayed LOCAL-only wording.");
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(
                    "The CLOUD Swing workspace could not open.",
                    failure.get());
        }
    }

    private static boolean hasLabel(Component component, String text) {
        if (component instanceof JLabel label
                && text.equals(label.getText())) {
            return true;
        }
        if (component instanceof Container container) {
            return Arrays.stream(container.getComponents())
                    .anyMatch(child -> hasLabel(child, text));
        }
        return false;
    }

    private static void verifyServerRestart(
            HttpRegistrationGateway gateway, Path controlDirectory)
            throws Exception {
        Files.writeString(controlDirectory.resolve("fixture-ready"),
                "READY\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        awaitSignal(controlDirectory.resolve("server-down.signal"));
        try {
            gateway.requestFinance("GET",
                    "/api/finance/accounts?page=0&size=100", "");
            throw new AssertionError(
                    "CLOUD access unexpectedly succeeded while offline.");
        } catch (RuntimeException expected) {
            require(gateway.getCloudConnectionState()
                            == CloudConnectionState.SERVER_UNAVAILABLE,
                    "The desktop did not expose Server unavailable.");
        }
        System.out.println("CloudServerUnavailableState: PASS");

        awaitSignal(controlDirectory.resolve("server-up.signal"));
        long deadline = System.nanoTime()
                + Duration.ofSeconds(90).toNanos();
        RuntimeException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                String accounts = gateway.requestFinance("GET",
                        "/api/finance/accounts?page=0&size=100", "");
                require(accounts.contains("ACCOUNT_CLOUD_SMOKE_MAIN"),
                        "CLOUD records were missing after server restart.");
                require(gateway.getCloudConnectionState()
                                == CloudConnectionState.CONNECTED,
                        "The desktop did not return to Connected.");
                System.out.println("CloudServerRestartPersistence: PASS");
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                Thread.sleep(500);
            }
        }
        throw new AssertionError(
                "CLOUD finance did not recover after server restart.",
                lastFailure);
    }

    private static void awaitSignal(Path path) throws Exception {
        long deadline = System.nanoTime()
                + Duration.ofMinutes(5).toNanos();
        while (!Files.isRegularFile(path)
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        require(Files.isRegularFile(path),
                "The live restart control signal was not received.");
    }

    private static HttpRegistrationGateway gateway() {
        return new HttpRegistrationGateway(
                ServerConfiguration.fromEnvironment());
    }

    private static Path verificationFile(Path directory, String email) {
        String safeName = email.replaceAll("[^A-Za-z0-9._-]", "_");
        return directory.resolve(safeName + ".txt");
    }

    private static Path awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!Files.isRegularFile(path) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        require(Files.isRegularFile(path),
                "Expected development verification mail was not written.");
        return path;
    }

    private static String secretLine(Path path, String prefix)
            throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected development mail field was missing."))
                .substring(prefix.length());
    }

    private static Path requiredDirectory(String name) {
        Path path = Path.of(requiredEnvironment(name))
                .toAbsolutePath().normalize();
        require(Files.isDirectory(path),
                name + " must identify an existing directory.");
        return path;
    }

    private static Path requiredExternalDirectory(
            String name, Path repositoryRoot) {
        Path path = requiredDirectory(name);
        require(!path.startsWith(repositoryRoot),
                name + " must remain outside the repository.");
        return path;
    }

    private static Path requiredExternalPath(
            String name, Path repositoryRoot) throws IOException {
        Path path = Path.of(requiredEnvironment(name))
                .toAbsolutePath().normalize();
        require(!path.startsWith(repositoryRoot),
                name + " must remain outside the repository.");
        Path parent = path.getParent();
        require(parent != null, name + " must have a parent directory.");
        Files.createDirectories(parent);
        return path;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(),
                name + " is required for the live CLOUD Swing test.");
        return value;
    }

    private static char[] randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return ("A1!" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes)).toCharArray();
    }

    private static void clearAndDelete(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return;
            }
            long length = Files.size(path);
            byte[] zeros = new byte[(int) Math.min(length, 4096)];
            try (var output = Files.newOutputStream(path,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                long remaining = length;
                while (remaining > 0) {
                    int count = (int) Math.min(remaining, zeros.length);
                    output.write(zeros, 0, count);
                    remaining -= count;
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The scoped database cleanup still removes the disposable user.
        }
    }

    private static void deleteRecursively(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Temporary files are best-effort shutdown cleanup.
                    }
                });
            }
        } catch (IOException ignored) {
            // Temporary files are best-effort shutdown cleanup.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
