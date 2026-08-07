package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "wealthora.registration.requires-admin-approval=false",
            "wealthora.registration.email-verification-required=false"
        })
@ActiveProfiles({"test", "dev-mail-sink"})
class SharedOnlineCoreEndpointTest {

    private static final String USER_A = "shared.a@northsouth.edu";
    private static final String USER_B = "shared.b@northsouth.edu";
    private static final String OWNER = "shared.owner@northsouth.edu";
    private static final String USER_PASSWORD = "SharedUser1!";
    private static final String OWNER_PASSWORD = "SharedOwner2!";

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

    @BeforeEach
    void setup() {
        deleteData();
        createOwner();
    }

    @AfterEach
    void cleanup() {
        deleteData();
    }

    @Test
    void sharedDataPersistsAcrossSessionsAndRemainsPrivate() throws Exception {
        register(USER_A, "Shared User A");
        UserAccount userA = users.findByEmail(USER_A).orElseThrow();
        assertTrue(userA.isEmailVerified());
        assertEquals(AccountStatus.ACTIVE, userA.getAccountStatus());
        assertTrue(roles.existsByUserIdAndRoleName(
                userA.getId(), UserRole.USER.name()));
        assertFalse(roles.existsByUserIdAndRoleName(
                userA.getId(), UserRole.ADMIN.name()));
        String storedHash = identities.findByUserIdAndProvider(
                userA.getId(), AuthProvider.PASSWORD).orElseThrow()
                .getPasswordHash();
        assertNotEquals(USER_PASSWORD, storedHash);
        assertTrue(storedHash.startsWith("{bcrypt-sha256}$2"));

        String deviceAToken = login(USER_A, USER_PASSWORD, "Device A");
        assertEquals(201, post("/api/finance/accounts",
                account("ACCOUNT_A_MAIN", "Main account", 100), deviceAToken)
                .statusCode());
        assertEquals(201, post("/api/finance/accounts",
                account("ACCOUNT_A_SAVE", "Savings", 0), deviceAToken).statusCode());
        assertEquals(201, post("/api/finance/income", """
                {"externalId":"INCOME_A_SHARED","source":"Salary","amount":200,
                "date":"2026-08-08","accountExternalId":"ACCOUNT_A_MAIN",
                "paymentMethod":"BANK_TRANSFER","tags":[],"note":""}
                """, deviceAToken).statusCode());
        assertEquals(201, post("/api/finance/expenses", """
                {"externalId":"shared-a-expense","description":"Food","amount":50,
                "date":"2026-08-08","accountExternalId":"ACCOUNT_A_MAIN",
                "categoryExternalId":"FOOD","paymentMethod":"CASH",
                "tags":[],"note":""}
                """, deviceAToken).statusCode());
        assertEquals(201, post("/api/finance/transfers", """
                {"externalId":"TRANSFER_A_SHARED","amount":25,
                "date":"2026-08-08","sourceAccountExternalId":"ACCOUNT_A_MAIN",
                "destinationAccountExternalId":"ACCOUNT_A_SAVE","tags":[],"note":""}
                """, deviceAToken).statusCode());
        assertEquals(204, post("/api/auth/logout", "{}", deviceAToken)
                .statusCode());

        String deviceBToken = login(USER_A, USER_PASSWORD, "Device B");
        String accounts = get("/api/finance/accounts", deviceBToken).body();
        assertTrue(accounts.contains("Main account"));
        assertTrue(accounts.contains("Savings"));
        assertTrue(get("/api/finance/transactions?page=0&size=20",
                deviceBToken).body().contains("\"totalElements\":4"));
        String dashboard = get(
                "/api/finance/reports/dashboard?month=2026-08",
                deviceBToken).body();
        assertTrue(dashboard.contains("\"income\":200.00"), dashboard);
        assertTrue(dashboard.contains("\"expenses\":50.00"), dashboard);

        register(USER_B, "Shared User B");
        String userBToken = login(USER_B, USER_PASSWORD, "Second user device");
        assertEquals(404, get("/api/finance/accounts/ACCOUNT_A_MAIN", userBToken)
                .statusCode());
        assertFalse(get("/api/finance/accounts", userBToken).body()
                .contains("Main account"));

        String ownerToken = login(OWNER, OWNER_PASSWORD, "OWNER device");
        String adminUsers = get("/api/admin/users", ownerToken).body();
        assertTrue(adminUsers.contains(USER_A));
        assertTrue(adminUsers.contains(USER_B));
        UUID userBId = users.findByEmail(USER_B).orElseThrow().getId();
        assertEquals(200, post("/api/admin/users/" + userBId
                + "/grant-admin", "{\"reason\":\"Support duty\","
                + "\"currentPassword\":\"" + OWNER_PASSWORD + "\"}",
                ownerToken).statusCode());
        assertTrue(roles.existsByUserIdAndRoleName(
                userBId, UserRole.ADMIN.name()));
        assertEquals(200, post("/api/admin/users/" + userBId
                + "/revoke-admin", "{\"reason\":\"Duty complete\","
                + "\"currentPassword\":\"" + OWNER_PASSWORD + "\"}",
                ownerToken).statusCode());
        assertFalse(roles.existsByUserIdAndRoleName(
                userBId, UserRole.ADMIN.name()));
        assertEquals(404, get("/api/finance/accounts/ACCOUNT_A_MAIN", ownerToken)
                .statusCode());
    }

    private void register(String email, String name) throws Exception {
        HttpResponse<String> response = post("/api/auth/register", "{"
                + "\"fullName\":\"" + name + "\","
                + "\"email\":\"" + email + "\","
                + "\"studentId\":\"\","
                + "\"password\":\"" + USER_PASSWORD + "\","
                + "\"passwordConfirmation\":\"" + USER_PASSWORD + "\","
                + "\"termsAccepted\":true}", null);
        assertEquals(201, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"accountStatus\":\"ACTIVE\""));
        assertTrue(response.body().contains("\"roles\":[\"USER\"]"));
    }

    private void createOwner() {
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        UUID id = UUID.randomUUID();
        UserAccount owner = new UserAccount(id, "Shared Owner", OWNER, null,
                AccountStatus.ACTIVE, now);
        owner.verifyEmail(AccountStatus.ACTIVE, now);
        users.save(owner);
        identities.save(new AuthenticationIdentity(UUID.randomUUID(), id,
                AuthProvider.PASSWORD,
                passwordEncoder.encode(OWNER_PASSWORD), now));
        roles.save(new UserRoleAssignment(id, UserRole.USER));
        roles.save(new UserRoleAssignment(id, UserRole.ADMIN));
        roles.save(new UserRoleAssignment(id, UserRole.OWNER));
    }

    private String login(
            String email, String password, String device) throws Exception {
        HttpResponse<String> response = post("/api/auth/login", "{"
                + "\"email\":\"" + email + "\","
                + "\"password\":\"" + password + "\","
                + "\"deviceLabel\":\"" + device + "\"}", null);
        assertEquals(200, response.statusCode(), response.body());
        return string(response.body(), "accessToken");
    }

    private static String account(String id, String name, int balance) {
        return "{\"externalId\":\"" + id + "\",\"name\":\"" + name
                + "\",\"accountType\":\"BANK\",\"currencyCode\":\"BDT\","
                + "\"openingBalance\":" + balance + ",\"iconName\":\"bank\","
                + "\"colorHex\":\"#1F7E60\",\"institutionName\":\"\","
                + "\"openedOn\":\"2026-08-08\",\"archived\":false}";
    }

    private HttpResponse<String> get(String path, String token)
            throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path)), token)
                .GET().build());
    }

    private HttpResponse<String> post(
            String path, String body, String token) throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path)), token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
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
        assertTrue(matcher.find(), "Missing JSON field: " + name);
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
