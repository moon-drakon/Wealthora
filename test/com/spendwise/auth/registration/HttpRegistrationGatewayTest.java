package com.spendwise.auth.registration;

import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthenticationAvailability;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.admin.AdminApplicationSettings;
import com.spendwise.auth.admin.AdminOverview;
import com.spendwise.auth.admin.AdminSecurityStatus;
import com.spendwise.auth.admin.DatabaseHealthStatus;
import com.spendwise.voice.SpeechProviderStatus;
import com.spendwise.voice.SpeechRecognitionResult;
import com.spendwise.voice.VoiceInputLanguage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class HttpRegistrationGatewayTest {

    private static final String EMAIL = "gateway.student@northsouth.edu";
    private static final String ACCESS =
            "access-token-with-at-least-thirty-two-characters";
    private static final String REFRESH =
            "refresh-token-with-at-least-thirty-two-characters";
    private static final String CURRENT_ID =
            "11111111-1111-1111-1111-111111111111";
    private static int passed;

    private HttpRegistrationGatewayTest() {
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        TestHandler handler = new TestHandler();
        server.createContext("/api/auth", handler::handle);
        server.createContext("/api/speech", handler::handle);
        server.createContext("/api/admin", handler::handle);
        server.createContext("/actuator", handler::handle);
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:"
                    + server.getAddress().getPort();
            test("server connection states are honest", () ->
                    connectionStates(baseUrl));
            test("generic recovery requests reach server", () ->
                    recoveryRequests(baseUrl, handler));
            test("online sessions are parsed and revoked", () ->
                    sessionManagement(baseUrl, handler));
            test("password change clears in-memory tokens", () ->
                    passwordChange(baseUrl));
            test("set password and logout-all clear tokens", () ->
                    setPasswordAndLogoutAll(baseUrl));
            test("authenticated speech status and result are parsed", () ->
                    speechRecognition(baseUrl));
            test("Google browser flow returns linked online session", () ->
                    googleBrowserFlow(baseUrl));
            test("administration views and actions use authenticated server contract",
                    () -> administration(baseUrl, handler));
            System.out.println("All " + passed
                    + " online authentication gateway tests passed.");
        } finally {
            server.stop(0);
        }
    }

    private static void connectionStates(String baseUrl) {
        AuthenticationAvailability missing = gateway("")
                .getAuthenticationAvailability();
        assertEquals("Server URL missing", missing.serverStatus());
        assertEquals("Email provider unavailable", missing.emailStatus());
        assertEquals("Google OAuth unavailable", missing.googleStatus());

        AuthenticationAvailability connected = gateway(baseUrl)
                .getAuthenticationAvailability();
        assertEquals("Connected", connected.serverStatus());
        assertEquals("Email provider configured", connected.emailStatus());
        assertEquals("Google OAuth configured", connected.googleStatus());

        AuthenticationAvailability unavailable = gateway(
                "http://127.0.0.1:1").getAuthenticationAvailability();
        assertEquals("Server unavailable", unavailable.serverStatus());
        assertEquals("Email provider unavailable", unavailable.emailStatus());
        assertEquals("Google OAuth unavailable", unavailable.googleStatus());
    }

    private static void recoveryRequests(
            String baseUrl, TestHandler handler) {
        HttpRegistrationGateway gateway = gateway(baseUrl);
        gateway.forgotPassword(EMAIL);
        gateway.resetPassword(EMAIL, "one-time-reset-token-value",
                "ChangedStudent2!".toCharArray());
        assertEquals(1, handler.forgotRequests);
        assertEquals(1, handler.resetRequests);
    }

    private static void sessionManagement(
            String baseUrl, TestHandler handler) {
        HttpRegistrationGateway gateway = gateway(baseUrl);
        UserSession signedIn = gateway.signIn(
                EMAIL, "GatewayStudent1!".toCharArray());
        assertEquals(EMAIL, signedIn.getEmail());
        List<AccountSession> sessions = gateway.listSessions();
        assertEquals(2, sessions.size());
        assertTrue(sessions.get(0).currentSession());
        assertEquals("Wealthora Desktop on Test OS",
                sessions.get(0).deviceLabel());
        gateway.revokeSession(sessions.get(1));
        assertTrue(gateway.hasActiveSession());
        assertEquals("22222222-2222-2222-2222-222222222222",
                handler.revokedIdentifier);
        gateway.revokeSession(sessions.get(0));
        assertFalse(gateway.hasActiveSession());
    }

    private static void passwordChange(String baseUrl) {
        HttpRegistrationGateway gateway = gateway(baseUrl);
        gateway.signIn(EMAIL, "GatewayStudent1!".toCharArray());
        gateway.changePassword("GatewayStudent1!".toCharArray(),
                "ChangedStudent3!".toCharArray());
        assertFalse(gateway.hasActiveSession());
    }

    private static void setPasswordAndLogoutAll(String baseUrl) {
        HttpRegistrationGateway gateway = gateway(baseUrl);
        gateway.signIn(EMAIL, "GatewayStudent1!".toCharArray());
        gateway.setPassword("AddedStudent4!".toCharArray());
        assertFalse(gateway.hasActiveSession());
        gateway.signIn(EMAIL, "GatewayStudent1!".toCharArray());
        gateway.logoutAll();
        assertFalse(gateway.hasActiveSession());
    }

    private static void speechRecognition(String baseUrl) {
        HttpRegistrationGateway gateway = gateway(baseUrl);
        gateway.signIn(EMAIL, "GatewayStudent1!".toCharArray());
        assertEquals(SpeechProviderStatus.READY,
                gateway.getSpeechStatus().status());
        byte[] audio = new byte[3_200];
        java.util.Arrays.fill(audio, (byte) 3);
        SpeechRecognitionResult result = gateway.recognizeSpeech(
                audio, 16_000, VoiceInputLanguage.AUTOMATIC);
        assertEquals("Paid 500 taka for lunch", result.transcript());
        assertEquals(VoiceInputLanguage.ENGLISH, result.detectedLanguage());
    }

    private static void googleBrowserFlow(String baseUrl) {
        AtomicReference<URI> opened = new AtomicReference<>();
        HttpRegistrationGateway gateway = new HttpRegistrationGateway(
                new ServerConfiguration(baseUrl), HttpClient.newHttpClient(),
                opened::set);
        assertTrue(gateway.getGoogleOAuthStatus().configured());
        UserSession session = gateway.continueWithGoogle();
        assertEquals("sub-google-test", session.getGoogleSubjectId());
        assertEquals(com.spendwise.auth.AuthProvider.GOOGLE,
                session.getProvider());
        assertEquals("https", opened.get().getScheme());
        assertTrue(gateway.hasActiveSession());
    }

    private static void administration(String baseUrl, TestHandler handler) {
        HttpRegistrationGateway gateway = gateway(baseUrl);
        gateway.signIn(EMAIL, "GatewayStudent1!".toCharArray());
        AdminOverview overview = gateway.getAdminOverview();
        assertEquals(4, overview.totalUsers());
        assertEquals(1, overview.pendingApproval());
        assertEquals(1, gateway.listAdminUsers().size());
        assertEquals(1, gateway.listPendingRegistrations().size());
        assertEquals(1, gateway.listPendingVerifications().size());
        assertEquals(1, gateway.listAdminAuditEvents().size());
        AdminSecurityStatus security = gateway.getAdminSecurityStatus();
        assertEquals(5, security.maximumFailedLoginAttempts());
        AdminApplicationSettings settings =
                gateway.getAdminApplicationSettings();
        assertTrue(settings.registrationRequiresAdminApproval());
        DatabaseHealthStatus database = gateway.getDatabaseHealth();
        assertEquals("PostgreSQL", database.databaseProduct());
        assertEquals("pending-user", gateway.approveRegistration(
                "pending-user", "NSU identity reviewed").getUserIdentifier());
        char[] password = "OwnerPassword1!".toCharArray();
        try {
            settings = gateway.updateAdminApplicationSettings(
                    false, password, "Open registration window");
        } finally {
            Arrays.fill(password, '\0');
        }
        assertFalse(settings.registrationRequiresAdminApproval());
        assertTrue(handler.lastAdminRequest.contains(
                "\"reason\":\"Open registration window\""));
        assertTrue(handler.lastAdminRequest.contains(
                "\"currentPassword\":\"OwnerPassword1!\""));
    }

    private static HttpRegistrationGateway gateway(String baseUrl) {
        System.setProperty("os.name", "Test OS");
        return new HttpRegistrationGateway(new ServerConfiguration(baseUrl));
    }

    private static void test(String name, ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false.");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected <" + expected
                    + "> but was <" + actual + ">.");
        }
    }

    private static final class TestHandler {

        private int forgotRequests;
        private int resetRequests;
        private String revokedIdentifier;
        private String lastAdminRequest = "";

        void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String requestBody = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (path.equals("/actuator/health")) {
                respond(exchange, 200, "{\"status\":\"UP\"}");
            } else if (path.equals("/api/auth/status")) {
                respond(exchange, 200,
                        "{\"emailProviderAvailable\":true,"
                        + "\"googleOAuthAvailable\":true}");
            } else if (path.equals("/api/auth/login")) {
                respond(exchange, 200, loginResponse());
            } else if (path.equals("/api/auth/google/status")) {
                respond(exchange, 200,
                        "{\"configured\":true,"
                        + "\"message\":\"Google OAuth ready.\","
                        + "\"redirectUri\":\"https://server.example/api/auth/google/callback\"}");
            } else if (path.equals("/api/auth/google/start")) {
                respond(exchange, 200,
                        "{\"flowIdentifier\":\"33333333-3333-3333-3333-333333333333\","
                        + "\"pollSecret\":\"poll-secret-with-at-least-thirty-two-characters\","
                        + "\"authorizationUrl\":\"https://accounts.google.com/o/oauth2/v2/auth?state=test\","
                        + "\"expiresAt\":\"2099-08-03T13:00:00Z\"}");
            } else if (path.equals("/api/auth/google/poll")) {
                respond(exchange, 200, "{\"status\":\"COMPLETED\","
                        + "\"message\":\"Google sign-in completed.\","
                        + "\"session\":" + googleSessionResponse() + "}");
            } else if (path.equals("/api/auth/forgot-password")) {
                forgotRequests++;
                respond(exchange, 202, "");
            } else if (path.equals("/api/auth/reset-password")) {
                resetRequests++;
                respond(exchange, 204, "");
            } else if (path.equals("/api/auth/sessions")
                    && method.equals("GET")) {
                requireAuthorization(exchange);
                respond(exchange, 200, sessionResponse());
            } else if (path.startsWith("/api/auth/sessions/")
                    && method.equals("DELETE")) {
                requireAuthorization(exchange);
                revokedIdentifier = path.substring(
                        "/api/auth/sessions/".length());
                respond(exchange, 204, "");
            } else if (path.equals("/api/auth/change-password")
                    || path.equals("/api/auth/set-password")
                    || path.equals("/api/auth/logout-all")) {
                requireAuthorization(exchange);
                respond(exchange, 204, "");
            } else if (path.equals("/api/speech/status")) {
                requireAuthorization(exchange);
                respond(exchange, 200,
                        "{\"provider\":\"Google Cloud Speech-to-Text\","
                        + "\"apiVersion\":\"V1\",\"ready\":true,"
                        + "\"message\":\"Provider ready.\"}");
            } else if (path.equals("/api/speech/recognize")) {
                requireAuthorization(exchange);
                assertTrue(requestBody.contains("\"sampleRateHertz\":16000"));
                assertTrue(requestBody.contains("\"language\":\"AUTOMATIC\""));
                respond(exchange, 200,
                        "{\"transcript\":\"Paid 500 taka for lunch\","
                        + "\"confidence\":0.93,"
                        + "\"detectedLanguage\":\"ENGLISH\","
                        + "\"detectedLocale\":\"en-US\","
                        + "\"audioDurationMilliseconds\":100}");
            } else if (path.equals("/api/admin/overview")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "{\"totalUsers\":4,"
                        + "\"activeUsers\":1,\"pendingApproval\":1,"
                        + "\"pendingVerification\":1,\"suspendedUsers\":1,"
                        + "\"disabledUsers\":0,\"owners\":1,"
                        + "\"administrators\":1,\"standardUsers\":2,"
                        + "\"failedLoginAttempts\":0}");
            } else if (path.equals("/api/admin/users")
                    && method.equals("GET")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "[" + userResponse(
                        "active-user", "ACTIVE", true) + "]");
            } else if (path.equals("/api/admin/pending-registrations")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "[" + userResponse(
                        "pending-user", "PENDING_APPROVAL", true) + "]");
            } else if (path.equals("/api/admin/verifications")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "[" + userResponse(
                        "verify-user", "PENDING_EMAIL_VERIFICATION", false)
                        + "]");
            } else if (path.equals("/api/admin/audit-logs")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "[{\"occurredAt\":"
                        + "\"2026-08-03T12:00:00Z\","
                        + "\"actorUserIdentifier\":\"admin-user\","
                        + "\"action\":\"REGISTRATION_APPROVED\","
                        + "\"targetUserIdentifier\":\"pending-user\","
                        + "\"outcome\":\"SUCCESS\","
                        + "\"reason\":\"NSU identity reviewed\"}]");
            } else if (path.equals("/api/admin/security")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "{\"passwordPolicy\":\"Strong\","
                        + "\"accessTokenExpiry\":\"15 minutes\","
                        + "\"refreshTokenExpiry\":\"30 days\","
                        + "\"lockDuration\":\"15 minutes\","
                        + "\"maximumFailedLoginAttempts\":5,"
                        + "\"verificationExpiry\":\"10 minutes\","
                        + "\"maximumVerificationAttempts\":5,"
                        + "\"passwordResetExpiry\":\"15 minutes\"}");
            } else if (path.equals("/api/admin/settings")) {
                requireAuthorization(exchange);
                lastAdminRequest = requestBody;
                respond(exchange, 200,
                        "{\"registrationRequiresAdminApproval\":"
                        + (method.equals("PUT") ? "false" : "true") + "}");
            } else if (path.equals("/api/admin/database-health")) {
                requireAuthorization(exchange);
                respond(exchange, 200, "{\"status\":\"UP\","
                        + "\"databaseProduct\":\"PostgreSQL\","
                        + "\"appliedMigrations\":3,\"users\":4,"
                        + "\"activeSessions\":2}");
            } else if (path.equals("/api/admin/users/pending-user/approve")) {
                requireAuthorization(exchange);
                lastAdminRequest = requestBody;
                respond(exchange, 200,
                        userResponse("pending-user", "ACTIVE", true));
            } else {
                respond(exchange, 404,
                        "{\"message\":\"Test endpoint not found.\"}");
            }
        }

        private static void requireAuthorization(HttpExchange exchange) {
            assertEquals("Bearer " + ACCESS,
                    exchange.getRequestHeaders().getFirst("Authorization"));
        }

        private static void respond(
                HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(status,
                    status == 204 ? -1 : content.length);
            if (content.length > 0) exchange.getResponseBody().write(content);
            exchange.close();
        }

        private static String loginResponse() {
            return "{\"accessToken\":\"" + ACCESS + "\","
                    + "\"refreshToken\":\"" + REFRESH + "\","
                    + "\"authenticatedAt\":\"2026-08-03T12:00:00Z\","
                    + "\"userIdentifier\":\"user-gateway\","
                    + "\"fullName\":\"Gateway Student\","
                    + "\"email\":\"" + EMAIL + "\","
                    + "\"emailVerified\":true,"
                    + "\"accountStatus\":\"ACTIVE\","
                    + "\"primaryAuthProvider\":\"LOCAL\","
                    + "\"googleSubjectId\":\"\","
                    + "\"createdAt\":\"2026-08-01T12:00:00Z\","
                    + "\"updatedAt\":\"2026-08-03T12:00:00Z\","
                    + "\"lastLoginAt\":\"2026-08-03T12:00:00Z\","
                    + "\"roles\":[\"USER\"]}";
        }

        private static String googleSessionResponse() {
            return "{\"accessToken\":\"" + ACCESS + "\","
                    + "\"refreshToken\":\"" + REFRESH + "\","
                    + "\"authenticatedAt\":\"2026-08-03T12:00:00Z\","
                    + "\"userIdentifier\":\"user-google\","
                    + "\"fullName\":\"Google Student\","
                    + "\"email\":\"" + EMAIL + "\","
                    + "\"emailVerified\":true,"
                    + "\"accountStatus\":\"ACTIVE\","
                    + "\"primaryAuthProvider\":\"GOOGLE\","
                    + "\"googleSubjectId\":\"sub-google-test\","
                    + "\"createdAt\":\"2026-08-01T12:00:00Z\","
                    + "\"updatedAt\":\"2026-08-03T12:00:00Z\","
                    + "\"lastLoginAt\":\"2026-08-03T12:00:00Z\","
                    + "\"roles\":[\"USER\"]}";
        }

        private static String sessionResponse() {
            Instant created = Instant.parse("2026-08-03T12:00:00Z");
            Instant expires = created.plusSeconds(900);
            return "[{\"sessionIdentifier\":\"" + CURRENT_ID + "\","
                    + "\"deviceLabel\":\"Wealthora Desktop on Test OS\","
                    + "\"createdAt\":\"" + created + "\","
                    + "\"accessExpiresAt\":\"" + expires + "\","
                    + "\"currentSession\":true},"
                    + "{\"sessionIdentifier\":"
                    + "\"22222222-2222-2222-2222-222222222222\","
                    + "\"deviceLabel\":\"Other Desktop\","
                    + "\"createdAt\":\"" + created.minusSeconds(60) + "\","
                    + "\"accessExpiresAt\":\"" + expires + "\","
                    + "\"currentSession\":false}]";
        }

        private static String userResponse(
                String identifier, String status, boolean verified) {
            return "{\"userIdentifier\":\"" + identifier + "\","
                    + "\"fullName\":\"Admin Test User\","
                    + "\"email\":\"admin.test@northsouth.edu\","
                    + "\"emailVerified\":" + verified + ","
                    + "\"accountStatus\":\"" + status + "\","
                    + "\"primaryAuthProvider\":\"LOCAL\","
                    + "\"googleSubjectId\":\"\","
                    + "\"createdAt\":\"2026-08-01T12:00:00Z\","
                    + "\"updatedAt\":\"2026-08-03T12:00:00Z\","
                    + "\"lastLoginAt\":null,\"roles\":[\"USER\"]}";
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
