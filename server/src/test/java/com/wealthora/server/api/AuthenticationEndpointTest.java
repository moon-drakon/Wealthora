package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
import com.wealthora.server.repository.LoginAttemptRepository;
import com.wealthora.server.repository.RefreshTokenRepository;
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev-mail-sink"})
class AuthenticationEndpointTest {

    private static final String EMAIL =
            "session.student@northsouth.edu";
    private static final String PASSWORD = "SessionStudent1!";
    private static final Path MAIL_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "wealthora-test-mail");

    @LocalServerPort private int port;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private SessionRecordRepository sessions;
    @Autowired private LoginAttemptRepository loginAttempts;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private EmailVerificationRepository verifications;
    @Autowired private AuthenticationIdentityRepository identities;
    @Autowired private UserRoleRepository roles;
    @Autowired private UserAccountRepository users;

    @BeforeEach
    void reset() throws Exception {
        refreshTokens.deleteAll();
        sessions.deleteAll();
        loginAttempts.deleteAll();
        auditLogs.deleteAll();
        verifications.deleteAll();
        identities.deleteAll();
        roles.deleteAll();
        users.deleteAll();
        Files.createDirectories(MAIL_DIRECTORY);
        Files.deleteIfExists(mailFile());
        createActiveAccount();
    }

    @AfterEach
    void cleanup() {
        refreshTokens.deleteAll();
        sessions.deleteAll();
        loginAttempts.deleteAll();
        auditLogs.deleteAll();
        verifications.deleteAll();
        identities.deleteAll();
        roles.deleteAll();
        users.deleteAll();
    }

    @Test
    void loginMeRefreshRotationAndLogoutUseOpaqueTokens() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", loginJson());
        assertEquals(200, login.statusCode());
        String firstAccess = string(login.body(), "accessToken");
        String firstRefresh = string(login.body(), "refreshToken");
        assertFalse(firstAccess.isBlank());
        assertFalse(firstRefresh.isBlank());
        assertNotEquals(firstAccess, firstRefresh);
        assertFalse(login.body().contains(PASSWORD));
        assertFalse(login.body().contains("passwordHash"));

        HttpResponse<String> current = get("/api/auth/me", firstAccess);
        assertEquals(200, current.statusCode());
        assertTrue(current.body().contains(EMAIL));

        HttpResponse<String> refreshed = post("/api/auth/refresh",
                "{\"refreshToken\":\"" + firstRefresh + "\"}");
        assertEquals(200, refreshed.statusCode());
        String secondAccess = string(refreshed.body(), "accessToken");
        String secondRefresh = string(refreshed.body(), "refreshToken");
        assertNotEquals(firstAccess, secondAccess);
        assertNotEquals(firstRefresh, secondRefresh);
        assertEquals(401, get("/api/auth/me", firstAccess).statusCode());
        assertEquals(200, get("/api/auth/me", secondAccess).statusCode());

        assertEquals(204, postAuthorized(
                "/api/auth/logout", "{}", secondAccess).statusCode());
        assertEquals(401, get("/api/auth/me", secondAccess).statusCode());
    }

    @Test
    void invalidCredentialsAreGenericAndRecorded() throws Exception {
        HttpResponse<String> wrong = post("/api/auth/login",
                loginJson().replace(PASSWORD, "WrongPassword1!"));
        HttpResponse<String> unknown = post("/api/auth/login",
                loginJson().replace(EMAIL,
                        "unknown.student@northsouth.edu"));
        assertEquals(401, wrong.statusCode());
        assertEquals(401, unknown.statusCode());
        assertTrue(wrong.body().contains(
                "Email or password is incorrect, or the account is unavailable."));
        assertTrue(unknown.body().contains(
                "Email or password is incorrect, or the account is unavailable."));
        assertEquals(2, loginAttempts.count());
    }

    @Test
    void refreshTokenReplayRevokesTheSession() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", loginJson());
        String firstRefresh = string(login.body(), "refreshToken");
        HttpResponse<String> refreshed = post("/api/auth/refresh",
                "{\"refreshToken\":\"" + firstRefresh + "\"}");
        String secondAccess = string(refreshed.body(), "accessToken");
        assertEquals(200, get("/api/auth/me", secondAccess).statusCode());
        assertEquals(401, post("/api/auth/refresh",
                "{\"refreshToken\":\"" + firstRefresh + "\"}")
                .statusCode());
        assertEquals(401, get("/api/auth/me", secondAccess).statusCode());
    }

    private void createActiveAccount() throws Exception {
        post("/api/auth/register", "{"
                + "\"fullName\":\"Session Student\","
                + "\"email\":\"" + EMAIL + "\","
                + "\"studentId\":\"2530000002\","
                + "\"password\":\"" + PASSWORD + "\","
                + "\"passwordConfirmation\":\"" + PASSWORD + "\","
                + "\"termsAccepted\":true}");
        String code = Files.readAllLines(mailFile()).stream()
                .filter(line -> line.startsWith("code="))
                .findFirst().orElseThrow().substring(5);
        post("/api/auth/verify-email", "{\"email\":\"" + EMAIL
                + "\",\"code\":\"" + code + "\"}");
        UserAccount user = users.findByEmail(EMAIL).orElseThrow();
        user.changeStatus(AccountStatus.ACTIVE, Instant.now());
        users.save(user);
    }

    private Path mailFile() {
        return MAIL_DIRECTORY.resolve(
                "session.student_northsouth.edu.txt");
    }

    private String loginJson() {
        return "{\"email\":\"" + EMAIL + "\","
                + "\"password\":\"" + PASSWORD + "\","
                + "\"deviceLabel\":\"Endpoint test\"}";
    }

    private HttpResponse<String> get(String path, String accessToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        return send(request);
    }

    private HttpResponse<String> post(String path, String json)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return send(request);
    }

    private HttpResponse<String> postAuthorized(
            String path, String json, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return send(request);
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
                + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + name);
        }
        return matcher.group(1);
    }
}
