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
import com.wealthora.server.service.ApplicationSettingsService;
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
class AdministrationEndpointTest {

    private static final String OWNER_EMAIL = "owner.admin@northsouth.edu";
    private static final String ADMIN_EMAIL = "admin.user@northsouth.edu";
    private static final String USER_EMAIL = "normal.user@northsouth.edu";
    private static final String OWNER_PASSWORD = "OwnerAdmin1!";
    private static final String ADMIN_PASSWORD = "AdminUser2!";
    private static final String USER_PASSWORD = "NormalUser3!";

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
    @Autowired private ApplicationSettingsService applicationSettings;

    private UUID ownerId;
    private UUID adminId;
    private UUID userId;
    private UUID pendingId;
    private UUID verificationId;

    @BeforeEach
    void setup() {
        deleteData();
        ownerId = account(OWNER_EMAIL, OWNER_PASSWORD, AccountStatus.ACTIVE,
                true, UserRole.ADMIN, UserRole.OWNER);
        adminId = account(ADMIN_EMAIL, ADMIN_PASSWORD, AccountStatus.ACTIVE,
                true, UserRole.ADMIN);
        userId = account(USER_EMAIL, USER_PASSWORD, AccountStatus.ACTIVE,
                true);
        pendingId = account("pending.user@northsouth.edu", "PendingUser4!",
                AccountStatus.PENDING_APPROVAL, true);
        verificationId = account("verify.user@northsouth.edu", "VerifyUser5!",
                AccountStatus.PENDING_EMAIL_VERIFICATION, false);
    }

    @AfterEach
    void cleanup() {
        deleteData();
    }

    @Test
    void userCannotAccessAdministration() throws Exception {
        String token = login(USER_EMAIL, USER_PASSWORD);
        assertEquals(403, get("/api/admin/overview", token).statusCode());
        assertEquals(403, post("/api/admin/users/" + pendingId + "/approve",
                "{\"reason\":\"Not authorized\"}", token).statusCode());
    }

    @Test
    void administratorManagesNormalUsersAndPendingQueues() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        HttpResponse<String> overview = get("/api/admin/overview", token);
        assertEquals(200, overview.statusCode());
        assertTrue(overview.body().contains("\"totalUsers\":5"));
        assertTrue(overview.body().contains("\"pendingApproval\":1"));
        assertTrue(get("/api/admin/pending-registrations", token).body()
                .contains("pending.user@northsouth.edu"));
        assertTrue(get("/api/admin/verifications", token).body()
                .contains("verify.user@northsouth.edu"));
        assertTrue(get("/api/admin/security", token).body()
                .contains("maximumFailedLoginAttempts"));
        assertTrue(get("/api/admin/settings", token).body()
                .contains("registrationRequiresAdminApproval"));
        assertTrue(get("/api/admin/database-health", token).body()
                .contains("\"status\":\"UP\""));

        assertEquals(200, post("/api/admin/users/" + pendingId + "/approve",
                "{\"reason\":\"Verified enrollment\"}", token).statusCode());
        assertEquals(AccountStatus.ACTIVE,
                users.findById(pendingId).orElseThrow().getAccountStatus());
        assertEquals(200, post("/api/admin/users/" + userId + "/suspend",
                "{\"reason\":\"Security review\"}", token).statusCode());
        assertEquals(AccountStatus.SUSPENDED,
                users.findById(userId).orElseThrow().getAccountStatus());
        assertEquals(403, post("/api/admin/users/" + ownerId + "/suspend",
                "{\"reason\":\"Forbidden\"}", token).statusCode());
        assertEquals(403, post("/api/admin/users/" + userId + "/grant-admin",
                "{\"reason\":\"Forbidden\",\"currentPassword\":\""
                + ADMIN_PASSWORD + "\"}", token).statusCode());
        assertTrue(get("/api/admin/audit-logs", token).body()
                .contains("REGISTRATION_APPROVED"));
    }

    @Test
    void ownerControlsAdministratorsSettingsAndRejection() throws Exception {
        String token = login(OWNER_EMAIL, OWNER_PASSWORD);
        String grantPath = "/api/admin/users/" + userId + "/grant-admin";
        assertEquals(401, post(grantPath,
                "{\"reason\":\"Support duty\","
                + "\"currentPassword\":\"WrongOwner9!\"}", token)
                .statusCode());
        assertEquals(200, post(grantPath,
                "{\"reason\":\"Support duty\",\"currentPassword\":\""
                + OWNER_PASSWORD + "\"}", token).statusCode());
        assertTrue(roles.existsByUserIdAndRoleName(
                userId, UserRole.ADMIN.name()));
        assertEquals(200, post("/api/admin/users/" + userId + "/revoke-admin",
                "{\"reason\":\"Duty ended\",\"currentPassword\":\""
                + OWNER_PASSWORD + "\"}", token).statusCode());
        assertFalse(roles.existsByUserIdAndRoleName(
                userId, UserRole.ADMIN.name()));

        HttpResponse<String> updated = put("/api/admin/settings",
                "{\"registrationRequiresAdminApproval\":false,"
                + "\"currentPassword\":\"" + OWNER_PASSWORD + "\","
                + "\"reason\":\"Development approval policy\"}", token);
        assertEquals(200, updated.statusCode());
        assertTrue(updated.body().contains(
                "\"registrationRequiresAdminApproval\":false"));
        assertFalse(applicationSettings.requiresAdminApproval());
        assertEquals(200, post("/api/admin/users/" + verificationId + "/reject",
                "{\"reason\":\"Registration withdrawn\"}", token)
                .statusCode());
        assertEquals(AccountStatus.DISABLED, users.findById(verificationId)
                .orElseThrow().getAccountStatus());
        assertEquals(403, post("/api/admin/users/" + ownerId + "/disable",
                "{\"reason\":\"Cannot disable owner\"}", token)
                .statusCode());
        assertEquals(400, post("/api/admin/users/" + adminId + "/disable",
                "{\"reason\":\"\"}", token).statusCode());
    }

    private UUID account(
            String email, String password, AccountStatus status,
            boolean verified, UserRole... extraRoles) {
        Instant now = Instant.parse("2026-08-03T07:00:00Z");
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount(id, email.substring(0,
                email.indexOf('@')).replace('.', ' '), email,
                email.substring(0, email.indexOf('@')), status, now);
        if (verified) account.verifyEmail(status, now);
        users.save(account);
        identities.save(new AuthenticationIdentity(UUID.randomUUID(), id,
                AuthProvider.PASSWORD, passwordEncoder.encode(password), now));
        roles.save(new UserRoleAssignment(id, UserRole.USER));
        for (UserRole role : extraRoles) {
            roles.save(new UserRoleAssignment(id, role));
        }
        return id;
    }

    private String login(String email, String password) throws Exception {
        HttpResponse<String> response = post("/api/auth/login", "{"
                + "\"email\":\"" + email + "\","
                + "\"password\":\"" + password + "\","
                + "\"deviceLabel\":\"Admin endpoint test\"}", null);
        assertEquals(200, response.statusCode());
        return string(response.body(), "accessToken");
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

    private HttpResponse<String> put(
            String path, String body, String token) throws Exception {
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
