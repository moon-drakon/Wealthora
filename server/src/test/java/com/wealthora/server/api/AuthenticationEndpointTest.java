package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
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
    @Autowired private PasswordResetTokenRepository passwordResetTokens;
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
        passwordResetTokens.deleteAll();
        auditLogs.deleteAll();
        verifications.deleteAll();
        identities.deleteAll();
        roles.deleteAll();
        users.deleteAll();
        Files.createDirectories(MAIL_DIRECTORY);
        Files.deleteIfExists(mailFile());
        Files.deleteIfExists(resetMailFile());
        createActiveAccount();
    }

    @AfterEach
    void cleanup() {
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

    @Test
    void forgotAndResetAreGenericSingleUseAndRevokeSessions()
            throws Exception {
        assertEquals(202, post("/api/auth/forgot-password",
                "{\"email\":\"unknown.student@northsouth.edu\"}")
                .statusCode());
        assertFalse(Files.exists(MAIL_DIRECTORY.resolve(
                "unknown.student_northsouth.edu.reset.txt")));

        String oldAccess = string(post(
                "/api/auth/login", loginJson()).body(), "accessToken");
        assertEquals(202, post("/api/auth/forgot-password",
                "{\"email\":\"" + EMAIL + "\"}").statusCode());
        String resetToken = Files.readAllLines(resetMailFile()).stream()
                .filter(line -> line.startsWith("token="))
                .findFirst().orElseThrow().substring(6);
        String newPassword = "UpdatedStudent2!";
        String resetJson = "{\"email\":\"" + EMAIL + "\","
                + "\"resetToken\":\"" + resetToken + "\","
                + "\"newPassword\":\"" + newPassword + "\","
                + "\"passwordConfirmation\":\"" + newPassword + "\"}";
        assertEquals(204, post(
                "/api/auth/reset-password", resetJson).statusCode());
        assertEquals(401, get("/api/auth/me", oldAccess).statusCode());
        assertEquals(401, post("/api/auth/login", loginJson()).statusCode());
        assertEquals(200, post("/api/auth/login",
                loginJson().replace(PASSWORD, newPassword)).statusCode());
        assertEquals(400, post(
                "/api/auth/reset-password", resetJson).statusCode());
    }

    @Test
    void passwordResetAttemptLimitIsPersisted() throws Exception {
        assertEquals(202, post("/api/auth/forgot-password",
                "{\"email\":\"" + EMAIL + "\"}").statusCode());
        String realToken = Files.readAllLines(resetMailFile()).stream()
                .filter(line -> line.startsWith("token="))
                .findFirst().orElseThrow().substring(6);
        String newPassword = "moon1234";
        String wrongToken = "wrong-token-value-with-more-than-32-characters";
        for (int attempt = 0; attempt < 5; attempt++) {
            String wrongJson = "{\"email\":\"" + EMAIL + "\","
                    + "\"resetToken\":\"" + wrongToken + "\","
                    + "\"newPassword\":\"" + newPassword + "\","
                    + "\"passwordConfirmation\":\"" + newPassword + "\"}";
            assertEquals(400, post(
                    "/api/auth/reset-password", wrongJson).statusCode());
        }
        assertEquals(5, passwordResetTokens.findFirstByUserIdOrderByCreatedAtDesc(
                users.findByEmail(EMAIL).orElseThrow().getId())
                .orElseThrow().getFailedAttempts());
        String realJson = "{\"email\":\"" + EMAIL + "\","
                + "\"resetToken\":\"" + realToken + "\","
                + "\"newPassword\":\"" + newPassword + "\","
                + "\"passwordConfirmation\":\"" + newPassword + "\"}";
        assertEquals(400, post(
                "/api/auth/reset-password", realJson).statusCode());
    }

    @Test
    void sessionsCanBeListedAndIndividuallyRevoked() throws Exception {
        String firstAccess = string(post(
                "/api/auth/login", loginJson()).body(), "accessToken");
        String secondAccess = string(post(
                "/api/auth/login", loginJson()).body(), "accessToken");
        HttpResponse<String> listed = get(
                "/api/auth/sessions", secondAccess);
        assertEquals(200, listed.statusCode());
        Matcher sessionsMatcher = Pattern.compile(
                "\\{\\\"sessionIdentifier\\\":\\\"([^\\\"]+)\\\".*?"
                + "\\\"currentSession\\\":(true|false)\\}",
                Pattern.DOTALL).matcher(listed.body());
        String otherSession = null;
        int count = 0;
        while (sessionsMatcher.find()) {
            count++;
            if (!Boolean.parseBoolean(sessionsMatcher.group(2))) {
                otherSession = sessionsMatcher.group(1);
            }
        }
        assertEquals(2, count);
        assertTrue(otherSession != null);
        assertEquals(204, delete("/api/auth/sessions/"
                + otherSession, secondAccess).statusCode());
        assertEquals(401, get("/api/auth/me", firstAccess).statusCode());
        assertEquals(200, get("/api/auth/me", secondAccess).statusCode());
    }

    @Test
    void passwordChangeRevokesEverySession() throws Exception {
        String firstAccess = string(post(
                "/api/auth/login", loginJson()).body(), "accessToken");
        String secondAccess = string(post(
                "/api/auth/login", loginJson()).body(), "accessToken");
        String newPassword = "ChangedStudent3!";
        String changeJson = "{\"currentPassword\":\"" + PASSWORD + "\","
                + "\"newPassword\":\"" + newPassword + "\","
                + "\"passwordConfirmation\":\"" + newPassword + "\"}";
        assertEquals(204, postAuthorized(
                "/api/auth/change-password", changeJson,
                secondAccess).statusCode());
        assertEquals(401, get("/api/auth/me", firstAccess).statusCode());
        assertEquals(401, get("/api/auth/me", secondAccess).statusCode());
        assertEquals(200, post("/api/auth/login",
                loginJson().replace(PASSWORD, newPassword)).statusCode());
    }

    @Test
    void passwordCanBeSetOnlyWhenIdentityIsMissing() throws Exception {
        String access = string(post(
                "/api/auth/login", loginJson()).body(), "accessToken");
        AuthenticationIdentity identity = identities
                .findByUserIdAndProvider(users.findByEmail(EMAIL)
                        .orElseThrow().getId(), AuthProvider.PASSWORD)
                .orElseThrow();
        identities.delete(identity);

        String newPassword = "AddedStudent4!";
        String setJson = "{\"newPassword\":\"" + newPassword + "\","
                + "\"passwordConfirmation\":\"" + newPassword + "\"}";
        assertEquals(204, postAuthorized(
                "/api/auth/set-password", setJson, access).statusCode());
        assertEquals(401, get("/api/auth/me", access).statusCode());
        String nextAccess = string(post("/api/auth/login",
                loginJson().replace(PASSWORD, newPassword)).body(),
                "accessToken");
        assertEquals(409, postAuthorized(
                "/api/auth/set-password", setJson, nextAccess).statusCode());
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

    private Path resetMailFile() {
        return MAIL_DIRECTORY.resolve(
                "session.student_northsouth.edu.reset.txt");
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

    private HttpResponse<String> delete(String path, String accessToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .DELETE().build();
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
