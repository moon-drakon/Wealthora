package com.wealthora.otp.relay;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public final class OtpRelayApplication {

    private static final int MAXIMUM_REQUEST_BYTES = 4 * 1024;

    private OtpRelayApplication() {
    }

    public static void main(String[] args) {
        RelayConfiguration configuration = RelayConfiguration.fromEnvironment();
        OtpRelayService service = new OtpRelayService(
                configuration.signingSecret(),
                new SmtpMailDelivery(configuration));
        HttpServer server = createServer(configuration);
        ExecutorService executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "wealthora-otp-request");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}");
                return;
            }
            respond(exchange, 200, "{\"status\":\"UP\"}");
        });
        server.createContext("/otp/request", requestHandler(service));
        server.createContext("/otp/verify", verificationHandler(service));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }, "wealthora-otp-shutdown"));
        server.start();
        System.out.println("Wealthora OTP relay listening on "
                + (configuration.loopbackHttp() ? "http" : "https")
                + "://" + configuration.bindAddress().getHostAddress()
                + ":" + configuration.port());
    }

    private static HttpHandler requestHandler(OtpRelayService service) {
        return exchange -> {
            if (!preparePost(exchange)) {
                return;
            }
            try {
                Map<String, Object> request = JsonSupport.parseObject(
                        readBody(exchange));
                JsonSupport.requireExactKeys(request, "email", "purpose",
                        "challengeId");
                OtpRelayService.AcceptedChallenge accepted =
                        service.requestCode(
                                JsonSupport.text(request, "email"),
                                OtpPurpose.parse(JsonSupport.text(
                                        request, "purpose")),
                                JsonSupport.text(request, "challengeId"),
                                exchange.getRemoteAddress().getAddress()
                                        .getHostAddress());
                String response = "{\"status\":\"ACCEPTED\","
                        + "\"challengeId\":"
                        + JsonSupport.quote(accepted.challengeIdentifier()) + ","
                        + "\"expiresInSeconds\":"
                        + accepted.expiresInSeconds() + ","
                        + "\"resendAfterSeconds\":"
                        + accepted.resendAfterSeconds() + "}";
                respond(exchange, 202, response);
            } catch (RateLimitException exception) {
                respond(exchange, 429,
                        "{\"error\":\"RATE_LIMITED\"}");
            } catch (InvalidRequestException exception) {
                respond(exchange, 400,
                        "{\"error\":\"INVALID_REQUEST\"}");
            } catch (RuntimeException exception) {
                respond(exchange, 503,
                        "{\"error\":\"DELIVERY_UNAVAILABLE\"}");
            }
        };
    }

    private static HttpHandler verificationHandler(OtpRelayService service) {
        return exchange -> {
            if (!preparePost(exchange)) {
                return;
            }
            try {
                Map<String, Object> request = JsonSupport.parseObject(
                        readBody(exchange));
                JsonSupport.requireExactKeys(request, "email", "purpose",
                        "challengeId", "code");
                boolean verified = service.verifyCode(
                        JsonSupport.text(request, "email"),
                        OtpPurpose.parse(JsonSupport.text(request, "purpose")),
                        JsonSupport.text(request, "challengeId"),
                        JsonSupport.text(request, "code"));
                respond(exchange, 200,
                        "{\"verified\":" + verified + "}");
            } catch (InvalidRequestException exception) {
                respond(exchange, 400,
                        "{\"error\":\"INVALID_REQUEST\"}");
            } catch (RuntimeException exception) {
                respond(exchange, 503,
                        "{\"error\":\"SERVICE_UNAVAILABLE\"}");
            }
        };
    }

    private static boolean preparePost(HttpExchange exchange)
            throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}");
            return false;
        }
        String contentType = exchange.getRequestHeaders()
                .getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(java.util.Locale.ROOT)
                        .startsWith("application/json")) {
            respond(exchange, 415,
                    "{\"error\":\"CONTENT_TYPE_REQUIRED\"}");
            return false;
        }
        String contentLength = exchange.getRequestHeaders()
                .getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > MAXIMUM_REQUEST_BYTES) {
                    respond(exchange, 413,
                            "{\"error\":\"REQUEST_TOO_LARGE\"}");
                    return false;
                }
            } catch (NumberFormatException exception) {
                respond(exchange, 400,
                        "{\"error\":\"INVALID_REQUEST\"}");
                return false;
            }
        }
        return true;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] bytes = input.readNBytes(MAXIMUM_REQUEST_BYTES + 1);
            if (bytes.length > MAXIMUM_REQUEST_BYTES) {
                throw new InvalidRequestException("Request is too large.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void respond(
            HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static HttpServer createServer(
            RelayConfiguration configuration) {
        InetSocketAddress address = new InetSocketAddress(
                configuration.bindAddress(), configuration.port());
        try {
            if (configuration.loopbackHttp()) {
                return HttpServer.create(address, 64);
            }
            HttpsServer server = HttpsServer.create(address, 64);
            server.setHttpsConfigurator(new HttpsConfigurator(
                    tlsContext(configuration)));
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "OTP relay could not bind its listening address.", exception);
        }
    }

    private static SSLContext tlsContext(RelayConfiguration configuration) {
        try (InputStream input = Files.newInputStream(configuration.keyStore())) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(input, configuration.keyStorePassword());
            KeyManagerFactory managers = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keyStore, configuration.keyStorePassword());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "OTP relay TLS configuration is invalid.", exception);
        }
    }
}
