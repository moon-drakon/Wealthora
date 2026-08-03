package com.spendwise.auth.registration;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthProvider;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.NsuEmailPolicy;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.UserRole;
import com.spendwise.auth.UserSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpRegistrationGateway implements RegistrationGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private final ServerConfiguration configuration;
    private final HttpClient client;
    private final PasswordService passwordService = new PasswordService();
    private String accessToken;
    private String refreshToken;

    public HttpRegistrationGateway(ServerConfiguration configuration) {
        this(configuration, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpRegistrationGateway(
            ServerConfiguration configuration, HttpClient client) {
        this.configuration = Objects.requireNonNull(configuration);
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public AuthenticatedUser register(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            char[] passwordConfirmation,
            boolean termsAccepted) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        passwordService.requireStrong(password);
        if (!Arrays.equals(password, passwordConfirmation)) {
            throw new AuthException("Password confirmation does not match.");
        }
        if (!termsAccepted) {
            throw new AuthException(
                    "Accept the terms and privacy notice to create an account.");
        }
        String body = "{" + field("fullName", fullName) + ","
                + field("email", normalizedEmail) + ","
                + field("studentId", studentIdentifier) + ","
                + field("password", new String(password)) + ","
                + field("passwordConfirmation",
                        new String(passwordConfirmation)) + ","
                + "\"termsAccepted\":true}";
        return user(post("/api/auth/register", body));
    }

    @Override
    public AuthenticatedUser verifyEmail(
            String email, String verificationCode) {
        String body = "{" + field("email",
                NsuEmailPolicy.requireInstitutionalEmail(email)) + ","
                + field("code", required(
                        verificationCode, "Verification code")) + "}";
        return user(post("/api/auth/verify-email", body));
    }

    @Override
    public void resendVerification(String email) {
        post("/api/auth/resend-verification", "{" + field("email",
                NsuEmailPolicy.requireInstitutionalEmail(email)) + "}");
    }

    @Override
    public void forgotPassword(String email) {
        post("/api/auth/forgot-password", "{" + field("email",
                NsuEmailPolicy.requireInstitutionalEmail(email)) + "}");
    }

    @Override
    public void resetPassword(
            String email, String resetToken, char[] newPassword) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        String token = required(resetToken, "Reset token");
        passwordService.requireStrong(newPassword);
        String passwordText = new String(newPassword);
        post("/api/auth/reset-password", "{"
                + field("email", normalizedEmail) + ","
                + field("resetToken", token) + ","
                + field("newPassword", passwordText) + ","
                + field("passwordConfirmation", passwordText) + "}");
    }

    @Override
    public synchronized void changePassword(
            char[] currentPassword, char[] newPassword) {
        if (currentPassword == null || currentPassword.length == 0) {
            throw new AuthException("Current password is required.");
        }
        passwordService.requireStrong(newPassword);
        String newPasswordText = new String(newPassword);
        send("POST", "/api/auth/change-password", "{"
                + field("currentPassword", new String(currentPassword)) + ","
                + field("newPassword", newPasswordText) + ","
                + field("passwordConfirmation", newPasswordText) + "}",
                requireToken(accessToken, "No online session is active."));
        clearSessionTokens();
    }

    @Override
    public synchronized void setPassword(char[] newPassword) {
        passwordService.requireStrong(newPassword);
        String passwordText = new String(newPassword);
        send("POST", "/api/auth/set-password", "{"
                + field("newPassword", passwordText) + ","
                + field("passwordConfirmation", passwordText) + "}",
                requireToken(accessToken, "No online session is active."));
        clearSessionTokens();
    }

    @Override
    public synchronized List<AccountSession> listSessions() {
        String json = send("GET", "/api/auth/sessions", "",
                requireToken(accessToken, "No online session is active."));
        List<AccountSession> result = new ArrayList<>();
        Matcher objects = Pattern.compile("\\{([^{}]*)}").matcher(json);
        while (objects.find()) {
            String object = objects.group();
            result.add(new AccountSession(
                    required(string(object, "sessionIdentifier"),
                            "Session identifier"),
                    string(object, "deviceLabel"),
                    instant(object, "createdAt", null),
                    instant(object, "accessExpiresAt", null),
                    bool(object, "currentSession")));
        }
        return List.copyOf(result);
    }

    @Override
    public synchronized void revokeSession(AccountSession session) {
        AccountSession requiredSession = Objects.requireNonNull(
                session, "Session is required.");
        send("DELETE", "/api/auth/sessions/"
                + requiredSession.sessionIdentifier(), "",
                requireToken(accessToken, "No online session is active."));
        if (requiredSession.currentSession()) {
            clearSessionTokens();
        }
    }

    @Override
    public synchronized void logoutAll() {
        try {
            send("POST", "/api/auth/logout-all", "{}",
                    requireToken(accessToken,
                            "No online session is active."));
        } finally {
            clearSessionTokens();
        }
    }

    @Override
    public synchronized UserSession signIn(String email, char[] password) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        if (password == null || password.length < 8) {
            throw new AuthException(
                    "Password must contain at least 8 characters.");
        }
        clearSessionTokens();
        String body = "{" + field("email", normalizedEmail) + ","
                + field("password", new String(password)) + ","
                + field("deviceLabel", desktopDeviceLabel()) + "}";
        return acceptSession(post("/api/auth/login", body));
    }

    @Override
    public synchronized UserSession refreshSession() {
        String currentRefresh = requireToken(
                refreshToken, "No online session can be refreshed.");
        try {
            return acceptSession(post("/api/auth/refresh", "{"
                    + field("refreshToken", currentRefresh) + "}"));
        } catch (RuntimeException exception) {
            clearSessionTokens();
            throw exception;
        }
    }

    @Override
    public synchronized void logout() {
        String currentAccess = accessToken;
        if (currentAccess == null || currentAccess.isBlank()) return;
        try {
            send("POST", "/api/auth/logout", "{}", currentAccess);
        } finally {
            clearSessionTokens();
        }
    }

    @Override
    public synchronized AuthenticatedUser getCurrentUser() {
        return user(send("GET", "/api/auth/me", "",
                requireToken(accessToken, "No online session is active.")));
    }

    @Override
    public synchronized boolean hasActiveSession() {
        return accessToken != null && !accessToken.isBlank()
                && refreshToken != null && !refreshToken.isBlank();
    }

    @Override
    public boolean isConfigured() {
        return configuration.isConfigured();
    }

    private String post(String path, String body) {
        return send("POST", path, body, null);
    }

    private String send(
            String method, String path, String body, String bearerToken) {
        URI target = URI.create(configuration.requireBaseUri() + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=UTF-8");
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpRequest request = switch (method) {
            case "GET" -> builder.GET().build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        };
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = string(response.body(), "message");
                throw new AuthException(message == null || message.isBlank()
                        ? "The authentication server rejected the request."
                        : message);
            }
            return response.body() == null ? "" : response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthException(
                    "The authentication request was interrupted.", exception);
        } catch (IOException exception) {
            throw new AuthException(
                    "The authentication server is unavailable.", exception);
        }
    }

    private UserSession acceptSession(String json) {
        AuthenticatedUser authenticatedUser = user(json);
        UserSession session = new UserSession(authenticatedUser,
                instant(json, "authenticatedAt",
                        authenticatedUser.getLastLoginAt() == null
                                ? Instant.now()
                                : authenticatedUser.getLastLoginAt()));
        String receivedAccess = required(
                string(json, "accessToken"), "Access token");
        String receivedRefresh = required(
                string(json, "refreshToken"), "Refresh token");
        accessToken = receivedAccess;
        refreshToken = receivedRefresh;
        return session;
    }

    private void clearSessionTokens() {
        accessToken = null;
        refreshToken = null;
    }

    private static AuthenticatedUser user(String json) {
        String identifier = required(string(json, "userIdentifier"), "User ID");
        String fullName = required(string(json, "fullName"), "Full name");
        String email = required(string(json, "email"), "Email");
        boolean verified = bool(json, "emailVerified");
        AccountStatus status = AccountStatus.valueOf(required(
                string(json, "accountStatus"), "Account status"));
        Instant created = instant(json, "createdAt", Instant.now());
        Instant updated = instant(json, "updatedAt", created);
        Instant lastLogin = instant(json, "lastLoginAt", null);
        Set<UserRole> roles = roleSet(json);
        return new AuthenticatedUser(identifier, fullName, email, verified,
                AuthProvider.LOCAL, "", status, created, updated, lastLogin,
                roles, optional(string(json, "studentId")), "System", "BDT");
    }

    private static Set<UserRole> roleSet(String json) {
        Matcher matcher = Pattern.compile(
                "\\\"roles\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
                .matcher(json);
        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        if (matcher.find()) {
            Matcher values = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"")
                    .matcher(matcher.group(1));
            while (values.find()) roles.add(UserRole.valueOf(
                    unescape(values.group(1))));
        }
        if (roles.isEmpty()) roles.add(UserRole.USER);
        return Set.copyOf(roles);
    }

    private static String string(String json, String name) {
        if (json == null) return null;
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                .matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static boolean bool(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*(true|false)")
                .matcher(json == null ? "" : json);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private static Instant instant(
            String json, String name, Instant fallback) {
        String value = string(json, name);
        return value == null || value.isBlank() ? fallback : Instant.parse(value);
    }

    private static String field(String name, String value) {
        return "\"" + escape(name) + "\":\""
                + escape(value == null ? "" : value) + "\"";
    }

    private static String desktopDeviceLabel() {
        String operatingSystem = System.getProperty("os.name", "Desktop");
        return "Wealthora Desktop on " + operatingSystem;
    }

    private static String requireToken(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new AuthException(message);
        }
        return value;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!escaped && character == '\\') {
                escaped = true;
            } else if (escaped) {
                result.append(switch (character) {
                    case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    default -> character;
                });
                escaped = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AuthException(name + " is required.");
        }
        return value.strip();
    }

    private static String optional(String value) {
        return value == null ? "" : value.strip();
    }
}
