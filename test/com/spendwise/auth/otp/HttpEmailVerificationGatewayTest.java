package com.spendwise.auth.otp;

import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class HttpEmailVerificationGatewayTest {

    private HttpEmailVerificationGatewayTest() {
    }

    public static void main(String[] args) throws Exception {
        configurationPolicy();
        requestAndVerifyUseNarrowJsonContract();
        malformedAndOversizedResponsesFailClosed();
        System.out.println("HttpEmailVerificationGatewayTest passed");
    }

    private static void configurationPolicy() {
        check(!new OtpRelayConfiguration("").isConfigured(),
                "blank relay URL");
        expect(AuthConfigurationException.class,
                () -> new OtpRelayConfiguration("http://relay.example")
                        .requireBaseUri());
        expect(AuthConfigurationException.class,
                () -> new OtpRelayConfiguration("https://relay.example/base")
                        .requireBaseUri());
        check(new OtpRelayConfiguration("http://127.0.0.1:8080")
                .requireBaseUri().getHost().equals("127.0.0.1"),
                "loopback development URL");
    }

    private static void requestAndVerifyUseNarrowJsonContract()
            throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> verifyBody = new AtomicReference<>();
        HttpServer server = server();
        server.createContext("/otp/request", exchange -> {
            requestBody.set(body(exchange));
            respond(exchange, 202, "application/json",
                    "{\"status\":\"ACCEPTED\","
                            + "\"challengeId\":\"challenge_12345678901234567890\","
                            + "\"expiresInSeconds\":600,"
                            + "\"resendAfterSeconds\":60}");
        });
        server.createContext("/otp/verify", exchange -> {
            verifyBody.set(body(exchange));
            respond(exchange, 200, "application/json",
                    "{\"verified\":true}");
        });
        server.start();
        try {
            HttpEmailVerificationGateway gateway = gateway(server);
            EmailOtpChallenge challenge = gateway.sendCode(
                    "Student@northsouth.edu", OtpPurpose.REGISTRATION, "");
            gateway.verifyCode(challenge.email(), challenge.purpose(),
                    challenge.challengeIdentifier(), "123456");
            String sent = requestBody.get();
            check(sent.equals("{\"email\":\"student@northsouth.edu\","
                    + "\"purpose\":\"REGISTRATION\","
                    + "\"challengeId\":\"\"}"), "request JSON");
            check(!sent.toLowerCase(java.util.Locale.ROOT)
                    .contains("password"), "no password field");
            check(!sent.toLowerCase(java.util.Locale.ROOT)
                    .contains("finance"), "no finance field");
            check(verifyBody.get().equals(
                    "{\"email\":\"student@northsouth.edu\","
                            + "\"purpose\":\"REGISTRATION\","
                            + "\"challengeId\":\"challenge_12345678901234567890\","
                            + "\"code\":\"123456\"}"), "verify JSON");
        } finally {
            server.stop(0);
        }
    }

    private static void malformedAndOversizedResponsesFailClosed()
            throws Exception {
        AtomicReference<String> response = new AtomicReference<>(
                "{\"unexpected\":true}");
        AtomicReference<String> contentType = new AtomicReference<>(
                "application/json");
        HttpServer server = server();
        server.createContext("/otp/request", exchange -> {
            body(exchange);
            respond(exchange, 200, contentType.get(), response.get());
        });
        server.start();
        try {
            HttpEmailVerificationGateway gateway = gateway(server);
            expect(AuthException.class, () -> gateway.sendCode(
                    "student@northsouth.edu", OtpPurpose.REGISTRATION, ""));
            response.set("{\"status\":\"ACCEPTED\","
                    + "\"challengeId\":\"challenge_12345678901234567890\","
                    + "\"expiresInSeconds\":600,"
                    + "\"resendAfterSeconds\":60}");
            contentType.set("text/plain");
            expect(AuthException.class, () -> gateway.sendCode(
                    "student@northsouth.edu", OtpPurpose.REGISTRATION, ""));
            contentType.set("application/json");
            response.set("x".repeat(17 * 1024));
            expect(AuthException.class, () -> gateway.sendCode(
                    "student@northsouth.edu", OtpPurpose.REGISTRATION, ""));
        } finally {
            server.stop(0);
        }
    }

    private static HttpEmailVerificationGateway gateway(HttpServer server) {
        return new HttpEmailVerificationGateway(new OtpRelayConfiguration(
                "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
    }

    private static String body(HttpExchange exchange) throws IOException {
        try (var input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(
            HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void expect(
            Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Unexpected exception", failure);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
