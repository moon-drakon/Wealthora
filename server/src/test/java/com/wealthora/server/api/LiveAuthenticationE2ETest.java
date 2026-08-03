package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class LiveAuthenticationE2ETest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private String activeStage = "STARTUP";

    public static void main(String[] arguments) {
        LiveAuthenticationE2ETest verifier =
                new LiveAuthenticationE2ETest();
        try {
            verifier.disposableUserCompletesTheLiveAuthenticationLifecycle();
        } catch (Throwable failure) {
            System.err.println(
                    "Live authentication E2E failed. Category=TEST_FAILURE"
                            + " Stage=" + verifier.activeStage
                            + " Type="
                            + failure.getClass().getSimpleName());
            for (StackTraceElement frame : failure.getStackTrace()) {
                if (frame.getClassName().equals(
                        LiveAuthenticationE2ETest.class.getName())) {
                    System.err.println("FailureLocation="
                            + frame.getMethodName() + ":"
                            + frame.getLineNumber());
                    break;
                }
            }
            if (failure
                    instanceof org.opentest4j.AssertionFailedError assertion
                    && assertion.getExpected() != null
                    && assertion.getActual() != null
                    && assertion.getExpected().getValue() instanceof Number
                    && assertion.getActual().getValue() instanceof Number) {
                System.err.println("ExpectedStatus="
                        + assertion.getExpected().getValue()
                        + " ActualStatus="
                        + assertion.getActual().getValue());
            }
            System.exit(1);
        }
    }

    @Test
    void disposableUserCompletesTheLiveAuthenticationLifecycle()
            throws Exception {
        assumeTrue(Boolean.parseBoolean(
                System.getenv("WEALTHORA_LIVE_AUTH_E2E")));

        String marker = UUID.randomUUID().toString().replace("-", "");
        String email = "wealthora.e2e." + marker + "@northsouth.edu";
        String unknownEmail = "wealthora.unknown." + marker
                + "@northsouth.edu";
        String password = randomPassword();
        String changedPassword = randomPassword();
        String resetPassword = randomPassword();
        Path mailDirectory = requiredMailDirectory();
        Path verificationFile = mailDirectory.resolve(
                safeMailName(email) + ".txt");
        Path resetFile = mailDirectory.resolve(
                safeMailName(email) + ".reset.txt");
        Path unknownResetFile = mailDirectory.resolve(
                safeMailName(unknownEmail) + ".reset.txt");

        try {
            activeStage = "PROVIDER_STATUS";
            providerStatusIsReady();
            activeStage = "REGISTRATION_VALIDATION";
            registrationValidationIsEnforced(
                    email, password, marker);

            activeStage = "REGISTRATION";
            assertStatus(201, post("/api/auth/register", json(
                    "fullName", "Wealthora E2E Student",
                    "email", email,
                    "studentId", marker.substring(0, 12),
                    "password", password,
                    "passwordConfirmation", password,
                    "termsAccepted", true)));
            assertStatus(409, post("/api/auth/register", json(
                    "fullName", "Wealthora E2E Student",
                    "email", email,
                    "studentId", marker.substring(0, 12),
                    "password", password,
                    "passwordConfirmation", password,
                    "termsAccepted", true)));
            assertStatus(401, login(email, password, "Pending user"));

            activeStage = "VERIFICATION_EXPIRY";
            String expiredCode = secretLine(
                    awaitFile(verificationFile), "code=");
            expireCurrentVerification(email);
            assertStatus(400, post("/api/auth/verify-email", json(
                    "email", email, "code", expiredCode)));

            activeStage = "VERIFICATION_RESEND";
            assertStatus(202, post("/api/auth/resend-verification",
                    json("email", email)));
            assertStatus(429, post("/api/auth/resend-verification",
                    json("email", email)));
            String code = secretLine(
                    awaitFile(verificationFile), "code=");
            String wrongCode = code.equals("000000")
                    ? "111111" : "000000";
            assertStatus(400, post("/api/auth/verify-email", json(
                    "email", email, "code", wrongCode)));
            HttpResponse<String> verified = post(
                    "/api/auth/verify-email",
                    json("email", email, "code", code));
            assertStatus(200, verified);
            assertEquals("ACTIVE", parse(verified).path(
                    "accountStatus").asText());
            assertStatus(400, post("/api/auth/verify-email", json(
                    "email", email, "code", code)));

            activeStage = "LOGIN_AND_SESSIONS";
            assertStatus(401, login(
                    email, randomPassword(), "Wrong password"));
            Session first = session(login(
                    email, password, "First live session"));
            Session second = session(login(
                    email, password, "Second live session"));
            assertCurrentUserIsRestricted(first.accessToken());

            JsonNode sessions = parse(get(
                    "/api/auth/sessions", second.accessToken()));
            assertEquals(2, sessions.size());
            String otherSession = "";
            for (JsonNode session : sessions) {
                if (!session.path("currentSession").asBoolean()) {
                    otherSession = session.path(
                            "sessionIdentifier").asText();
                }
            }
            assertFalse(otherSession.isBlank());
            assertStatus(204, delete(
                    "/api/auth/sessions/" + otherSession,
                    second.accessToken()));
            assertStatus(401, get(
                    "/api/auth/me", first.accessToken()));
            assertStatus(200, get(
                    "/api/auth/me", second.accessToken()));

            Session refreshed = session(post("/api/auth/refresh",
                    json("refreshToken", second.refreshToken())));
            assertStatus(401, get(
                    "/api/auth/me", second.accessToken()));
            assertStatus(200, get(
                    "/api/auth/me", refreshed.accessToken()));
            assertStatus(401, post("/api/auth/refresh",
                    json("refreshToken", second.refreshToken())));
            assertStatus(401, get(
                    "/api/auth/me", refreshed.accessToken()));

            activeStage = "PASSWORD_CHANGE";
            Session changeSession = session(login(
                    email, password, "Password change"));
            activeStage = "PASSWORD_CHANGE_WRONG_CURRENT";
            assertStatus(401, postAuthorized(
                    "/api/auth/change-password",
                    json("currentPassword", randomPassword(),
                            "newPassword", changedPassword,
                            "passwordConfirmation", changedPassword),
                    changeSession.accessToken()));
            activeStage = "PASSWORD_CHANGE_COMPLETE";
            assertStatus(204, postAuthorized(
                    "/api/auth/change-password",
                    json("currentPassword", password,
                            "newPassword", changedPassword,
                            "passwordConfirmation", changedPassword),
                    changeSession.accessToken()));
            activeStage = "PASSWORD_CHANGE_SESSION_REVOCATION";
            assertStatus(401, get(
                    "/api/auth/me", changeSession.accessToken()));
            activeStage = "PASSWORD_CHANGE_OLD_PASSWORD";
            assertStatus(401, login(
                    email, password, "Old password"));

            activeStage = "PASSWORD_RECOVERY";
            Session beforeReset = session(login(
                    email, changedPassword, "Password reset"));
            assertStatus(202, post("/api/auth/forgot-password",
                    json("email", unknownEmail)));
            assertFalse(Files.exists(unknownResetFile));
            assertStatus(202, post("/api/auth/forgot-password",
                    json("email", email)));
            String resetToken = secretLine(
                    awaitFile(resetFile), "token=");
            assertStatus(400, post("/api/auth/reset-password",
                    json("email", email,
                            "resetToken", "invalid-reset-token-value"
                                    + "-longer-than-thirty-two-characters",
                            "newPassword", resetPassword,
                            "passwordConfirmation", resetPassword)));
            String resetRequest = json(
                    "email", email,
                    "resetToken", resetToken,
                    "newPassword", resetPassword,
                    "passwordConfirmation", resetPassword);
            assertStatus(204, post(
                    "/api/auth/reset-password", resetRequest));
            assertStatus(401, get(
                    "/api/auth/me", beforeReset.accessToken()));
            assertStatus(400, post(
                    "/api/auth/reset-password", resetRequest));
            assertStatus(401, login(
                    email, changedPassword, "Pre-reset password"));

            activeStage = "FAILED_LOGIN_PROTECTION";
            verifyFailedLoginProtection(email, resetPassword);
            activeStage = "ACCOUNT_STATUS_PROTECTION";
            verifyAccountStatusProtection(email, resetPassword);
            activeStage = "SESSION_EXPIRY";
            verifySessionExpiry(email, resetPassword);

            activeStage = "LOGOUT";
            Session logoutOne = session(login(
                    email, resetPassword, "Logout one"));
            Session logoutAll = session(login(
                    email, resetPassword, "Logout all"));
            assertStatus(204, postAuthorized(
                    "/api/auth/logout-all", "{}", logoutAll.accessToken()));
            assertStatus(401, get(
                    "/api/auth/me", logoutOne.accessToken()));
            assertStatus(401, get(
                    "/api/auth/me", logoutAll.accessToken()));

            Session finalSession = session(login(
                    email, resetPassword, "Final logout"));
            assertStatus(204, postAuthorized(
                    "/api/auth/logout", "{}",
                    finalSession.accessToken()));
            assertStatus(401, get(
                    "/api/auth/me", finalSession.accessToken()));

            activeStage = "AUDIT";
            assertAuditCoverage(email);
            System.out.println("RegistrationValidation: PASS");
            System.out.println("VerificationLifecycle: PASS");
            System.out.println("PasswordAuthentication: PASS");
            System.out.println("PasswordRecovery: PASS");
            System.out.println("SessionLifecycle: PASS");
            System.out.println("FailedLoginProtection: PASS");
            System.out.println("AccountStatusProtection: PASS");
            System.out.println("UserRoleRestriction: PASS");
            System.out.println("AuthenticationAudit: PASS");
        } finally {
            String failedStage = activeStage;
            activeStage = "CLEANUP";
            cleanupTestUser(email);
            clearAndDelete(verificationFile);
            clearAndDelete(resetFile);
            clearAndDelete(unknownResetFile);
            activeStage = failedStage;
        }
    }

    private void providerStatusIsReady() throws Exception {
        HttpResponse<String> response = getPublic("/api/auth/status");
        assertStatus(200, response);
        JsonNode status = parse(response);
        assertTrue(status.path("emailProviderAvailable").asBoolean());
        assertTrue(status.path("googleOAuthAvailable").asBoolean());
    }

    private void registrationValidationIsEnforced(
            String email, String password, String marker) throws Exception {
        assertStatus(400, post("/api/auth/register", json(
                "fullName", "Invalid Domain",
                "email", "wealthora.e2e." + marker + "@example.com",
                "studentId", marker.substring(0, 12),
                "password", password,
                "passwordConfirmation", password,
                "termsAccepted", true)));
        assertStatus(400, post("/api/auth/register", json(
                "fullName", "Weak Password",
                "email", email,
                "studentId", marker.substring(0, 12),
                "password", "Abc1234",
                "passwordConfirmation", "Abc1234",
                "termsAccepted", true)));
        assertStatus(400, post("/api/auth/register", json(
                "fullName", "Terms Missing",
                "email", email,
                "studentId", marker.substring(0, 12),
                "password", password,
                "passwordConfirmation", password,
                "termsAccepted", false)));
    }

    private void assertCurrentUserIsRestricted(String accessToken)
            throws Exception {
        HttpResponse<String> response = get(
                "/api/auth/me", accessToken);
        assertStatus(200, response);
        JsonNode user = parse(response);
        assertEquals("ACTIVE", user.path("accountStatus").asText());
        assertEquals(1, user.path("roles").size());
        assertEquals("USER", user.path("roles").get(0).asText());
        assertStatus(403, get("/api/admin/overview", accessToken));
    }

    private void verifyFailedLoginProtection(
            String email, String password) throws Exception {
        String wrongPassword = randomPassword();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertStatus(401, login(
                    email, wrongPassword, "Lockout check"));
        }
        assertStatus(401, login(email, password, "Locked account"));
        resetLoginProtection(email);
        Session recovered = session(login(
                email, password, "Lockout recovery"));
        assertStatus(204, postAuthorized(
                "/api/auth/logout", "{}", recovered.accessToken()));
    }

    private void verifyAccountStatusProtection(
            String email, String password) throws Exception {
        setAccountStatus(email, "SUSPENDED");
        assertStatus(401, login(
                email, password, "Suspended account"));
        setAccountStatus(email, "DISABLED");
        assertStatus(401, login(
                email, password, "Disabled account"));
        setAccountStatus(email, "ACTIVE");
    }

    private void verifySessionExpiry(
            String email, String password) throws Exception {
        Session session = session(login(
                email, password, "Expired session"));
        expireSession(email, "Expired session");
        assertStatus(401, get(
                "/api/auth/me", session.accessToken()));
    }

    private void expireCurrentVerification(String email) throws Exception {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "update email_verifications set "
                                + "expires_at = current_timestamp "
                                + "- interval '1 minute', "
                                + "sent_at = current_timestamp "
                                + "- interval '2 minutes' "
                                + "where user_id = "
                                + "(select id from users where email = ?) "
                                + "and consumed_at is null")) {
            statement.setString(1, email);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void resetLoginProtection(String email) throws Exception {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "update users set failed_login_attempts = 0, "
                                + "locked_until = null where email = ?")) {
            statement.setString(1, email);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void setAccountStatus(String email, String status)
            throws Exception {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "update users set account_status = ?, "
                                + "updated_at = current_timestamp "
                                + "where email = ?")) {
            statement.setString(1, status);
            statement.setString(2, email);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void expireSession(String email, String deviceLabel)
            throws Exception {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "update sessions set expires_at = current_timestamp "
                                + "- interval '1 minute' where user_id = "
                                + "(select id from users where email = ?) "
                                + "and device_label = ? "
                                + "and revoked_at is null")) {
            statement.setString(1, email);
            statement.setString(2, deviceLabel);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void assertAuditCoverage(String email) throws Exception {
        Set<String> expected = Set.of(
                "REGISTRATION_CREATED",
                "EMAIL_VERIFIED",
                "PASSWORD_CHANGED",
                "PASSWORD_RESET_REQUESTED",
                "PASSWORD_RESET_COMPLETED");
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select distinct action from audit_logs "
                                + "where actor_user_id = "
                                + "(select id from users where email = ?)")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                java.util.HashSet<String> actions =
                        new java.util.HashSet<>();
                while (result.next()) {
                    actions.add(result.getString(1));
                }
                assertTrue(actions.containsAll(expected));
            }
        }
    }

    private void cleanupTestUser(String email) {
        try (Connection connection = databaseConnection()) {
            connection.setAutoCommit(false);
            UUID userId = null;
            try (PreparedStatement find = connection.prepareStatement(
                    "select id from users where email = ?")) {
                find.setString(1, email);
                try (ResultSet result = find.executeQuery()) {
                    if (result.next()) {
                        userId = result.getObject(1, UUID.class);
                    }
                }
            }
            if (userId != null) {
                deleteByUser(connection,
                        "delete from audit_logs "
                                + "where actor_user_id = ? "
                                + "or target_user_id = ?",
                        userId, true);
                deleteByUser(connection,
                        "delete from login_attempts where user_id = ?",
                        userId, false);
                deleteByUser(connection,
                        "delete from users where id = ?",
                        userId, false);
            }
            connection.commit();
        } catch (Exception exception) {
            fail("Live authentication cleanup failed.");
        }
    }

    private void deleteByUser(Connection connection, String sql,
            UUID userId, boolean twoParameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            if (twoParameters) {
                statement.setObject(2, userId);
            }
            statement.executeUpdate();
        }
    }

    private Connection databaseConnection() throws Exception {
        String url = requiredEnvironment("DATABASE_URL");
        Properties properties = new Properties();
        properties.setProperty("user",
                requiredEnvironment("DATABASE_USERNAME"));
        properties.setProperty("password",
                requiredEnvironment("DATABASE_PASSWORD"));
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");
        return DriverManager.getConnection(url, properties);
    }

    private Session session(HttpResponse<String> response) throws Exception {
        assertStatus(200, response);
        JsonNode body = parse(response);
        String accessToken = body.path("accessToken").asText();
        String refreshToken = body.path("refreshToken").asText();
        assertFalse(accessToken.isBlank());
        assertFalse(refreshToken.isBlank());
        assertNotEquals(accessToken, refreshToken);
        return new Session(accessToken, refreshToken);
    }

    private HttpResponse<String> login(
            String email, String password, String deviceLabel)
            throws Exception {
        return post("/api/auth/login", json(
                "email", email,
                "password", password,
                "deviceLabel", deviceLabel));
    }

    private HttpResponse<String> getPublic(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET().build();
        return send(request);
    }

    private HttpResponse<String> get(
            String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        return send(request);
    }

    private HttpResponse<String> post(String path, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    private HttpResponse<String> postAuthorized(
            String path, String body, String accessToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    private HttpResponse<String> delete(
            String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE().build();
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        String base = requiredEnvironment("WEALTHORA_SERVER_URL");
        return URI.create(base + path);
    }

    private JsonNode parse(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body());
    }

    private String json(Object... pairs) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return JSON.writeValueAsString(values);
    }

    private void assertStatus(
            int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(),
                "Live authentication request returned an unexpected status.");
    }

    private Path requiredMailDirectory() {
        Path path = Path.of(requiredEnvironment(
                "WEALTHORA_DEV_MAIL_DIR")).toAbsolutePath().normalize();
        assertFalse(path.startsWith(Path.of("").toAbsolutePath()));
        return path;
    }

    private Path awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(15).toNanos();
        while (!Files.isRegularFile(path)
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(Files.isRegularFile(path),
                "Expected development mail was not written.");
        return path;
    }

    private String secretLine(Path path, String prefix) throws Exception {
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected development mail field was missing."))
                .substring(prefix.length());
    }

    private void clearAndDelete(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                long length = Files.size(path);
                byte[] zeros = new byte[(int) Math.min(length, 4096)];
                try (var output = Files.newOutputStream(path,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    long remaining = length;
                    while (remaining > 0) {
                        int count = (int) Math.min(
                                remaining, zeros.length);
                        output.write(zeros, 0, count);
                        remaining -= count;
                    }
                }
                Files.deleteIfExists(path);
            }
        } catch (Exception exception) {
            fail("Development mail cleanup failed.");
        }
    }

    private String safeMailName(String email) {
        return email.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return "A1!" + Base64.getUrlEncoder()
                .withoutPadding().encodeToString(bytes);
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(),
                "Live authentication configuration is incomplete.");
        return value;
    }

    private record Session(String accessToken, String refreshToken) {
    }
}
