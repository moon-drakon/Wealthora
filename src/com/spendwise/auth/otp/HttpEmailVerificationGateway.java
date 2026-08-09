package com.spendwise.auth.otp;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.NsuEmailPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** HTTPS client for the finance-independent OTP relay. */
public final class HttpEmailVerificationGateway
        implements EmailVerificationGateway {

    private static final int MAXIMUM_RESPONSE_BYTES = 16 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private final OtpRelayConfiguration configuration;
    private final HttpClient client;

    public HttpEmailVerificationGateway(OtpRelayConfiguration configuration) {
        this(configuration, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    HttpEmailVerificationGateway(
            OtpRelayConfiguration configuration, HttpClient client) {
        this.configuration = Objects.requireNonNull(configuration);
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public boolean isConfigured() {
        return configuration.isConfigured();
    }

    @Override
    public EmailOtpChallenge sendCode(
            String normalizedEmail, OtpPurpose purpose,
            String existingChallengeIdentifier) {
        String email = NsuEmailPolicy.requireInstitutionalEmail(normalizedEmail);
        OtpPurpose requiredPurpose = Objects.requireNonNull(
                purpose, "OTP purpose is required.");
        String existing = existingChallengeIdentifier == null
                ? "" : existingChallengeIdentifier.strip();
        String body = "{" + field("email", email) + ","
                + field("purpose", requiredPurpose.name()) + ","
                + field("challengeId", existing) + "}";
        Map<String, Object> response = FlatJson.parse(
                send("/otp/request", body));
        requireExactKeys(response, "status", "challengeId",
                "expiresInSeconds", "resendAfterSeconds");
        if (!"ACCEPTED".equals(response.get("status"))) {
            throw malformed();
        }
        String challengeId = text(response, "challengeId");
        if (!challengeId.matches("[A-Za-z0-9_-]{20,120}")) {
            throw malformed();
        }
        long expiry = number(response, "expiresInSeconds");
        long resend = number(response, "resendAfterSeconds");
        if (expiry < 1 || expiry > 600 || resend < 0 || resend < 60
                || resend > expiry) {
            throw malformed();
        }
        Instant now = Instant.now();
        return new EmailOtpChallenge(challengeId, email, requiredPurpose,
                now.plusSeconds(expiry), now.plusSeconds(resend));
    }

    @Override
    public void verifyCode(
            String normalizedEmail, OtpPurpose purpose,
            String challengeIdentifier, String code) {
        String email = NsuEmailPolicy.requireInstitutionalEmail(normalizedEmail);
        OtpPurpose requiredPurpose = Objects.requireNonNull(
                purpose, "OTP purpose is required.");
        String challengeId = required(challengeIdentifier,
                "Challenge identifier");
        String verificationCode = required(code, "Verification code");
        if (!verificationCode.matches("[0-9]{6}")) {
            throw new AuthException(
                    "Verification code must contain exactly six digits.");
        }
        String body = "{" + field("email", email) + ","
                + field("purpose", requiredPurpose.name()) + ","
                + field("challengeId", challengeId) + ","
                + field("code", verificationCode) + "}";
        Map<String, Object> response = FlatJson.parse(
                send("/otp/verify", body));
        requireExactKeys(response, "verified");
        if (!Boolean.TRUE.equals(response.get("verified"))) {
            throw new AuthException(
                    "The verification code is invalid or expired.");
        }
    }

    private String send(String path, String body) {
        URI endpoint = configuration.requireBaseUri().resolve(path);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            String contentType = response.headers()
                    .firstValue("Content-Type").orElse("");
            String responseBody;
            try (InputStream input = response.body()) {
                byte[] bytes = input.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
                if (bytes.length > MAXIMUM_RESPONSE_BYTES) {
                    throw malformed();
                }
                responseBody = new String(bytes, StandardCharsets.UTF_8);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AuthException(response.statusCode() == 429
                        ? "Too many OTP requests. Wait before trying again."
                        : "Email verification is temporarily unavailable. Try again later.");
            }
            if (!contentType.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("application/json")) {
                throw malformed();
            }
            return responseBody;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthException(
                    "Email verification was interrupted. Try again.", exception);
        } catch (IOException exception) {
            throw new AuthException(
                    "The email verification service is unavailable. Existing offline features are unaffected.",
                    exception);
        }
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        throw new AuthException("OTP request data is invalid.");
                    }
                    escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AuthException(name + " is required.");
        }
        return value.strip();
    }

    private static String text(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw malformed();
        }
        return text;
    }

    private static long number(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Long number)) {
            throw malformed();
        }
        return number;
    }

    private static void requireExactKeys(
            Map<String, Object> values, String... expected) {
        if (!values.keySet().equals(java.util.Set.of(expected))) {
            throw malformed();
        }
    }

    private static AuthException malformed() {
        return new AuthException(
                "The email verification service returned an invalid response.");
    }

    /** Strict parser for the relay's intentionally flat JSON responses. */
    static final class FlatJson {
        private final String source;
        private int position;

        private FlatJson(String source) {
            this.source = Objects.requireNonNull(source);
        }

        static Map<String, Object> parse(String source) {
            return new FlatJson(source).object();
        }

        private Map<String, Object> object() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            whitespace();
            take('{');
            whitespace();
            if (peek('}')) {
                position++;
                finish();
                return values;
            }
            while (true) {
                String name = string();
                whitespace();
                take(':');
                whitespace();
                Object value = value();
                if (values.putIfAbsent(name, value) != null) {
                    throw malformed();
                }
                whitespace();
                if (peek('}')) {
                    position++;
                    finish();
                    return values;
                }
                take(',');
                whitespace();
            }
        }

        private Object value() {
            if (peek('"')) {
                return string();
            }
            if (source.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }
            if (source.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            int start = position;
            if (peek('-')) {
                position++;
            }
            while (position < source.length()
                    && Character.isDigit(source.charAt(position))) {
                position++;
            }
            if (start == position) {
                throw malformed();
            }
            try {
                return Long.parseLong(source.substring(start, position));
            } catch (NumberFormatException exception) {
                throw malformed();
            }
        }

        private String string() {
            take('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char character = source.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    if (position >= source.length()) {
                        throw malformed();
                    }
                    char escaped = source.charAt(position++);
                    value.append(switch (escaped) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw malformed();
                    });
                } else if (character < 0x20) {
                    throw malformed();
                } else {
                    value.append(character);
                }
            }
            throw malformed();
        }

        private void take(char expected) {
            if (!peek(expected)) {
                throw malformed();
            }
            position++;
        }

        private boolean peek(char value) {
            return position < source.length()
                    && source.charAt(position) == value;
        }

        private void whitespace() {
            while (position < source.length()
                    && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void finish() {
            whitespace();
            if (position != source.length()) {
                throw malformed();
            }
        }
    }
}
