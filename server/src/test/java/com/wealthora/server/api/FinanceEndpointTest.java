package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRole;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.repository.ApplicationSettingRepository;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
import com.wealthora.server.repository.GoogleOAuthFlowRepository;
import com.wealthora.server.repository.LoginAttemptRepository;
import com.wealthora.server.repository.PasswordResetTokenRepository;
import com.wealthora.server.repository.RefreshTokenRepository;
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev-mail-sink"})
class FinanceEndpointTest {

    private static final String USER_EMAIL = "finance.user@northsouth.edu";
    private static final String ADMIN_EMAIL = "finance.admin@northsouth.edu";
    private static final String OWNER_EMAIL = "finance.owner@northsouth.edu";
    private static final String PASSWORD = "FinanceUser1!";

    @LocalServerPort private int port;
    @Autowired private ApplicationSettingRepository settings;
    @Autowired private GoogleOAuthFlowRepository googleFlows;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private SessionRecordRepository sessions;
    @Autowired private LoginAttemptRepository loginAttempts;
    @Autowired private PasswordResetTokenRepository passwordResetTokens;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private EmailVerificationRepository verifications;
    @Autowired private AuthenticationIdentityRepository identities;
    @Autowired private UserRoleRepository roles;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userToken;
    private String adminToken;
    private String ownerToken;

    @BeforeEach
    void setup() throws Exception {
        deleteData();
        account(USER_EMAIL);
        account(ADMIN_EMAIL, UserRole.ADMIN);
        account(OWNER_EMAIL, UserRole.ADMIN, UserRole.OWNER);
        userToken = login(USER_EMAIL);
        adminToken = login(ADMIN_EMAIL);
        ownerToken = login(OWNER_EMAIL);
    }

    @AfterEach
    void cleanup() {
        deleteData();
    }

    @Test
    void authenticationAndRolesNeverExpandFinanceOwnership() throws Exception {
        assertEquals(401, get("/api/finance/accounts", null).statusCode());
        long auditCount = auditLogs.count();

        assertEquals(201, post("/api/finance/accounts",
                accountJson("ACCOUNT_USER_ONLY", "User private", "BDT", 20),
                userToken).statusCode());
        assertEquals(201, post("/api/finance/accounts",
                accountJson("ACCOUNT_SHARED", "User shared", "BDT", 10),
                userToken).statusCode());
        assertEquals(201, post("/api/finance/accounts",
                accountJson("ACCOUNT_SHARED", "Admin shared", "BDT", 30),
                adminToken).statusCode());
        assertEquals(201, post("/api/finance/accounts",
                accountJson("ACCOUNT_SHARED", "Owner shared", "BDT", 40),
                ownerToken).statusCode());

        assertEquals(404, get("/api/finance/accounts/ACCOUNT_USER_ONLY",
                adminToken).statusCode());
        assertEquals(404, get("/api/finance/accounts/ACCOUNT_USER_ONLY",
                ownerToken).statusCode());
        assertTrue(get("/api/finance/accounts/ACCOUNT_SHARED", adminToken)
                .body().contains("Admin shared"));
        assertFalse(get("/api/finance/accounts/ACCOUNT_SHARED", adminToken)
                .body().contains("User shared"));
        assertEquals(auditCount, auditLogs.count(),
                "Ordinary private finance operations must not fill audit logs");
    }

    @Test
    void ledgerChangesBalancesAtomicallyAndRejectsDuplicateTransfers()
            throws Exception {
        createAccount("ACCOUNT_MAIN", "Main", "BDT", 1000, userToken);
        createAccount("ACCOUNT_SAVINGS", "Savings", "BDT", 100, userToken);
        createAccount("ACCOUNT_USD", "USD", "USD", 50, userToken);

        assertEquals(201, post("/api/finance/income", """
                {"externalId":"INCOME_SALARY","source":"Salary",
                "amount":200,"date":"2026-08-03",
                "accountExternalId":"ACCOUNT_MAIN",
                "paymentMethod":"BANK_TRANSFER","tags":["work"],"note":""}
                """, userToken).statusCode());
        assertEquals(201, post("/api/finance/expenses", """
                {"externalId":"expense-2026-001","description":"Lunch",
                "amount":50,"date":"2026-08-03",
                "accountExternalId":"ACCOUNT_MAIN",
                "categoryExternalId":"FOOD","paymentMethod":"CASH",
                "tags":["meal"],"note":""}
                """, userToken).statusCode());
        String transfer = """
                {"externalId":"TRANSFER_SAVE","amount":300,
                "date":"2026-08-03","sourceAccountExternalId":"ACCOUNT_MAIN",
                "destinationAccountExternalId":"ACCOUNT_SAVINGS",
                "tags":["reserve"],"note":""}
                """;
        assertEquals(201, post("/api/finance/transfers", transfer, userToken)
                .statusCode());
        assertBalance("ACCOUNT_MAIN", "850", userToken);
        assertBalance("ACCOUNT_SAVINGS", "400", userToken);

        HttpResponse<String> duplicate = post(
                "/api/finance/transfers", transfer, userToken);
        assertEquals(409, duplicate.statusCode());
        assertTrue(duplicate.body().contains("FINANCE_DUPLICATE"));
        assertBalance("ACCOUNT_MAIN", "850", userToken);
        assertBalance("ACCOUNT_SAVINGS", "400", userToken);

        HttpResponse<String> crossCurrency = post("/api/finance/transfers",
                transfer.replace("TRANSFER_SAVE", "TRANSFER_BAD_CURRENCY")
                        .replace("ACCOUNT_SAVINGS", "ACCOUNT_USD"), userToken);
        assertEquals(400, crossCurrency.statusCode());
        assertBalance("ACCOUNT_MAIN", "850", userToken);
        assertBalance("ACCOUNT_USD", "50", userToken);

        String transactions = get(
                "/api/finance/transactions?page=0&size=10", userToken).body();
        assertTrue(transactions.contains("\"totalElements\":4"));
        assertTrue(transactions.contains("\"transferDirection\":\"OUT\""));
        assertTrue(transactions.contains("\"transferDirection\":\"IN\""));
        assertEquals(400, get("/api/finance/accounts?size=101", userToken)
                .statusCode());
    }

    @Test
    void planningAndReportsUseValidatedOwnedReferences() throws Exception {
        createAccount("ACCOUNT_PLAN", "Plan", "BDT", 500, userToken);
        assertEquals(201, post("/api/finance/categories", """
                {"externalId":"CUSTOM_RESEARCH","name":"Research",
                "parentExternalId":"EDUCATION","archived":false}
                """, userToken).statusCode());

        assertEquals(200, put("/api/finance/budgets/monthly/2026-08", """
                {"month":"2026-08","overallLimit":1000,
                "categoryLimits":{"CUSTOM_RESEARCH":300}}
                """, userToken).statusCode());
        assertEquals(201, post("/api/finance/budgets/plans", """
                {"externalId":"BUDGET_TERM","name":"Term budget",
                "startDate":"2026-08-01","endDate":"2026-12-31",
                "overallLimit":5000,"categoryLimits":{"EDUCATION":2000},
                "rolloverMode":"CARRY_UNUSED","active":true}
                """, userToken).statusCode());
        assertEquals(201, post("/api/finance/recurring", """
                {"externalId":"RECURRING_RENT","entryType":"EXPENSE",
                "amount":100,"description":"Rent","categoryExternalId":"BILLS",
                "sourceAccountExternalId":"ACCOUNT_PLAN",
                "destinationAccountExternalId":null,"frequency":"MONTHLY",
                "interval":1,"startDate":"2026-08-01","endDate":null,
                "nextDueDate":"2026-09-01","recurringKind":"BILL",
                "reminderDays":3,"active":true}
                """, userToken).statusCode());
        assertEquals(201, post("/api/finance/goals", """
                {"externalId":"GOAL_LAPTOP","name":"Laptop",
                "targetAmount":1500,"targetDate":"2026-12-01",
                "linkedAccountExternalId":"ACCOUNT_PLAN","active":true}
                """, userToken).statusCode());
        assertEquals(201, post(
                "/api/finance/goals/GOAL_LAPTOP/contributions", """
                {"externalId":"CONTRIBUTION_ONE","date":"2026-08-03",
                "amount":125,"note":"First deposit"}
                """, userToken).statusCode());
        assertTrue(get("/api/finance/goals", userToken).body()
                .contains("\"contributedAmount\":125.00"));

        assertEquals(201, post("/api/finance/debts", """
                {"externalId":"DEBT_BOOKS","direction":"BORROWED",
                "counterparty":"Family","originalAmount":300,
                "dueDate":"2026-12-31","note":"Books"}
                """, userToken).statusCode());
        assertEquals(201, post(
                "/api/finance/debts/DEBT_BOOKS/repayments", """
                {"externalId":"REPAYMENT_ONE","date":"2026-08-03",
                "amount":50,"note":"Installment"}
                """, userToken).statusCode());
        assertTrue(get("/api/finance/debts", userToken).body()
                .contains("\"remainingAmount\":250.00"));

        HttpResponse<String> privateReference = post(
                "/api/finance/goals", """
                {"externalId":"GOAL_FOREIGN","name":"Foreign",
                "targetAmount":100,"targetDate":"2026-12-01",
                "linkedAccountExternalId":"ACCOUNT_PLAN","active":true}
                """, adminToken);
        assertEquals(404, privateReference.statusCode());
        String report = get(
                "/api/finance/reports/dashboard?month=2026-08", userToken).body();
        assertTrue(report.contains("\"month\":\"2026-08\""));
        assertTrue(report.contains("\"budgetLimit\":1000.00"));
        assertFalse(report.toLowerCase().contains("exception"));
    }

    private void createAccount(String id, String name, String currency,
            int openingBalance, String token) throws Exception {
        assertEquals(201, post("/api/finance/accounts",
                accountJson(id, name, currency, openingBalance), token)
                .statusCode());
    }

    private void assertBalance(String id, String expected, String token)
            throws Exception {
        String body = get("/api/finance/accounts/" + id, token).body();
        Matcher matcher = Pattern.compile(
                "\\\"currentBalance\\\"\\s*:\\s*(-?[0-9.]+)")
                .matcher(body);
        assertTrue(matcher.find(), body);
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(
                new java.math.BigDecimal(matcher.group(1))), body);
    }

    private static String accountJson(
            String id, String name, String currency, int openingBalance) {
        return "{" +
                "\"externalId\":\"" + id + "\"," +
                "\"name\":\"" + name + "\"," +
                "\"accountType\":\"BANK\"," +
                "\"currencyCode\":\"" + currency + "\"," +
                "\"openingBalance\":" + openingBalance + "," +
                "\"iconName\":\"bank\",\"colorHex\":\"#1F7E60\"," +
                "\"institutionName\":\"Test bank\"," +
                "\"openedOn\":\"2026-08-01\",\"archived\":false}";
    }

    private void account(String email, UserRole... extraRoles) {
        Instant now = Instant.parse("2026-08-03T07:00:00Z");
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount(id, email.substring(0,
                email.indexOf('@')).replace('.', ' '), email,
                email.substring(0, email.indexOf('@')), AccountStatus.ACTIVE,
                now);
        account.verifyEmail(AccountStatus.ACTIVE, now);
        users.save(account);
        identities.save(new AuthenticationIdentity(UUID.randomUUID(), id,
                AuthProvider.PASSWORD, passwordEncoder.encode(PASSWORD), now));
        roles.save(new UserRoleAssignment(id, UserRole.USER));
        for (UserRole role : extraRoles) {
            roles.save(new UserRoleAssignment(id, role));
        }
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = post("/api/auth/login", "{" +
                "\"email\":\"" + email + "\"," +
                "\"password\":\"" + PASSWORD + "\"," +
                "\"deviceLabel\":\"Finance endpoint test\"}", null);
        assertEquals(200, response.statusCode(), response.body());
        return string(response.body(), "accessToken");
    }

    private HttpResponse<String> get(String path, String token)
            throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path)), token)
                .GET().build());
    }

    private HttpResponse<String> post(String path, String body, String token)
            throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path)), token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpResponse<String> put(String path, String body, String token)
            throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path)), token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private static HttpRequest.Builder authorized(
            HttpRequest.Builder builder, String token) {
        return token == null ? builder
                : builder.header("Authorization", "Bearer " + token);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String string(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                .matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + name);
        return matcher.group(1);
    }

    private void deleteData() {
        settings.deleteAll();
        googleFlows.deleteAll();
        refreshTokens.deleteAll();
        sessions.deleteAll();
        loginAttempts.deleteAll();
        passwordResetTokens.deleteAll();
        auditLogs.deleteAll();
        verifications.deleteAll();
        identities.deleteAll();
        roles.deleteAll();
        users.deleteAll();
    }
}
