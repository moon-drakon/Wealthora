package com.spendwise.auth.registration;

import com.spendwise.auth.AccountSession;
import com.spendwise.auth.UserSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

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
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:"
                    + server.getAddress().getPort();
            test("generic recovery requests reach server", () ->
                    recoveryRequests(baseUrl, handler));
            test("online sessions are parsed and revoked", () ->
                    sessionManagement(baseUrl, handler));
            test("password change clears in-memory tokens", () ->
                    passwordChange(baseUrl));
            test("set password and logout-all clear tokens", () ->
                    setPasswordAndLogoutAll(baseUrl));
            System.out.println("All " + passed
                    + " online authentication gateway tests passed.");
        } finally {
            server.stop(0);
        }
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

        void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            exchange.getRequestBody().readAllBytes();
            if (path.equals("/api/auth/login")) {
                respond(exchange, 200, loginResponse());
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
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
