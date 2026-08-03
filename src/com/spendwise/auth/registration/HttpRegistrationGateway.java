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
import com.spendwise.auth.GoogleOAuthStatus;
import com.spendwise.auth.admin.AdminApplicationSettings;
import com.spendwise.auth.admin.AdminOverview;
import com.spendwise.auth.admin.AdminSecurityStatus;
import com.spendwise.auth.admin.AdministrationGateway;
import com.spendwise.auth.admin.DatabaseHealthStatus;
import com.spendwise.auth.audit.AuditAction;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.voice.SpeechBackendStatus;
import com.spendwise.voice.SpeechProviderStatus;
import com.spendwise.voice.SpeechRecognitionResult;
import com.spendwise.voice.VoiceInputLanguage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpRegistrationGateway
        implements RegistrationGateway, AdministrationGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration SPEECH_TIMEOUT = Duration.ofSeconds(45);
    private final ServerConfiguration configuration;
    private final HttpClient client;
    private final PasswordService passwordService = new PasswordService();
    private final BrowserLauncher browserLauncher;
    private String accessToken;
    private String refreshToken;

    public HttpRegistrationGateway(ServerConfiguration configuration) {
        this(configuration, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(),
                new SystemBrowserLauncher());
    }

    HttpRegistrationGateway(
            ServerConfiguration configuration, HttpClient client) {
        this(configuration, client, new SystemBrowserLauncher());
    }

    HttpRegistrationGateway(
            ServerConfiguration configuration, HttpClient client,
            BrowserLauncher browserLauncher) {
        this.configuration = Objects.requireNonNull(configuration);
        this.client = Objects.requireNonNull(client);
        this.browserLauncher = Objects.requireNonNull(browserLauncher);
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
    public GoogleOAuthStatus getGoogleOAuthStatus() {
        if (!configuration.isConfigured()) {
            return new GoogleOAuthStatus(false,
                    "WEALTHORA_SERVER_URL is not configured.", "");
        }
        try {
            String json = send("GET", "/api/auth/google/status", "", null);
            return new GoogleOAuthStatus(bool(json, "configured"),
                    required(string(json, "message"), "Google OAuth status"),
                    optional(string(json, "redirectUri")));
        } catch (AuthException exception) {
            return new GoogleOAuthStatus(false, exception.getMessage(), "");
        }
    }

    @Override
    public synchronized UserSession continueWithGoogle() {
        GoogleOAuthStatus status = getGoogleOAuthStatus();
        if (!status.configured()) throw new AuthException(status.message());
        clearSessionTokens();
        String started = post("/api/auth/google/start", "{"
                + field("deviceLabel", desktopDeviceLabel()) + "}");
        String flowIdentifier = required(
                string(started, "flowIdentifier"), "OAuth flow identifier");
        char[] pollSecret = required(
                string(started, "pollSecret"), "OAuth polling secret")
                .toCharArray();
        try {
            URI authorizationUri = URI.create(required(
                    string(started, "authorizationUrl"),
                    "Google authorization URL"));
            if (!"https".equalsIgnoreCase(authorizationUri.getScheme())
                    || !"accounts.google.com".equalsIgnoreCase(
                            authorizationUri.getHost())) {
                throw new AuthException(
                        "The server returned an invalid Google authorization URL.");
            }
            Instant expiresAt = instant(started, "expiresAt",
                    Instant.now().plusSeconds(180));
            browserLauncher.open(authorizationUri);
            while (Instant.now().isBefore(expiresAt)) {
                String secretText = new String(pollSecret);
                String response;
                try {
                    response = post("/api/auth/google/poll", "{"
                            + field("flowIdentifier", flowIdentifier) + ","
                            + field("pollSecret", secretText) + "}");
                } finally {
                    secretText = null;
                }
                String flowStatus = required(
                        string(response, "status"), "Google OAuth flow status");
                if ("COMPLETED".equals(flowStatus)) {
                    return acceptSession(response);
                }
                if ("FAILED".equals(flowStatus)) {
                    throw new AuthException(required(
                            string(response, "message"),
                            "Google Sign-In result"));
                }
                sleepBeforePoll();
            }
            throw new AuthException(
                    "Google Sign-In expired. Start again.");
        } finally {
            Arrays.fill(pollSecret, '\0');
        }
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
    public synchronized SpeechBackendStatus getSpeechStatus() {
        if (!hasActiveSession()) {
            return new SpeechBackendStatus(SpeechProviderStatus.NOT_CONFIGURED,
                    "Sign in with an online account to use speech recognition.");
        }
        try {
            String json = send("GET", "/api/speech/status", "",
                    accessToken);
            boolean ready = bool(json, "ready");
            return new SpeechBackendStatus(
                    ready ? SpeechProviderStatus.READY
                            : SpeechProviderStatus.NOT_CONFIGURED,
                    required(string(json, "message"), "Speech status"));
        } catch (AuthException exception) {
            return new SpeechBackendStatus(SpeechProviderStatus.UNAVAILABLE,
                    exception.getMessage());
        }
    }

    @Override
    public synchronized SpeechRecognitionResult recognizeSpeech(
            byte[] linearPcmAudio,
            int sampleRateHertz,
            VoiceInputLanguage language) {
        Objects.requireNonNull(linearPcmAudio, "Speech audio is required.");
        Objects.requireNonNull(language, "Speech language is required.");
        String body = "{" + field("audioBase64",
                Base64.getEncoder().encodeToString(linearPcmAudio)) + ","
                + "\"sampleRateHertz\":" + sampleRateHertz + ","
                + field("language", language.name()) + "}";
        String json = send("POST", "/api/speech/recognize", body,
                requireToken(accessToken,
                        "No online session is active."), SPEECH_TIMEOUT);
        return new SpeechRecognitionResult(
                required(string(json, "transcript"), "Speech transcript"),
                number(json, "confidence"),
                VoiceInputLanguage.valueOf(required(
                        string(json, "detectedLanguage"),
                        "Detected language")));
    }

    @Override
    public synchronized boolean hasOnlineSession() {
        return hasActiveSession();
    }

    @Override
    public synchronized AdminOverview getAdminOverview() {
        String json = adminGet("/api/admin/overview");
        return new AdminOverview(integer(json, "totalUsers"),
                integer(json, "activeUsers"),
                integer(json, "pendingApproval"),
                integer(json, "pendingVerification"),
                integer(json, "suspendedUsers"),
                integer(json, "disabledUsers"),
                integer(json, "owners"),
                integer(json, "administrators"),
                integer(json, "standardUsers"),
                integer(json, "failedLoginAttempts"),
                "Use Backup tab", "Server database");
    }

    @Override
    public synchronized List<AuthenticatedUser> listAdminUsers() {
        return users(adminGet("/api/admin/users"));
    }

    @Override
    public synchronized List<AuthenticatedUser> listPendingRegistrations() {
        return users(adminGet("/api/admin/pending-registrations"));
    }

    @Override
    public synchronized List<AuthenticatedUser> listPendingVerifications() {
        return users(adminGet("/api/admin/verifications"));
    }

    @Override
    public synchronized List<AuditEvent> listAdminAuditEvents() {
        String json = adminGet("/api/admin/audit-logs");
        List<AuditEvent> events = new ArrayList<>();
        for (String object : objects(json)) {
            events.add(new AuditEvent(instant(object, "occurredAt", Instant.EPOCH),
                    optional(string(object, "actorUserIdentifier")),
                    AuditAction.fromExternal(string(object, "action")),
                    optional(string(object, "targetUserIdentifier")),
                    optional(string(object, "outcome")),
                    optional(string(object, "reason"))));
        }
        return List.copyOf(events);
    }

    @Override
    public synchronized AdminSecurityStatus getAdminSecurityStatus() {
        String json = adminGet("/api/admin/security");
        return new AdminSecurityStatus(
                required(string(json, "passwordPolicy"), "Password policy"),
                required(string(json, "accessTokenExpiry"), "Access expiry"),
                required(string(json, "refreshTokenExpiry"), "Refresh expiry"),
                required(string(json, "lockDuration"), "Lock duration"),
                integer(json, "maximumFailedLoginAttempts"),
                required(string(json, "verificationExpiry"),
                        "Verification expiry"),
                integer(json, "maximumVerificationAttempts"),
                required(string(json, "passwordResetExpiry"),
                        "Password reset expiry"));
    }

    @Override
    public synchronized AdminApplicationSettings getAdminApplicationSettings() {
        String json = adminGet("/api/admin/settings");
        return new AdminApplicationSettings(bool(json,
                "registrationRequiresAdminApproval"), true);
    }

    @Override
    public synchronized DatabaseHealthStatus getDatabaseHealth() {
        String json = adminGet("/api/admin/database-health");
        return new DatabaseHealthStatus(
                required(string(json, "status"), "Database status"),
                required(string(json, "databaseProduct"), "Database product"),
                (long) number(json, "appliedMigrations"),
                (long) number(json, "users"),
                (long) number(json, "activeSessions"));
    }

    @Override
    public synchronized AuthenticatedUser approveRegistration(
            String userIdentifier, String reason) {
        return adminAction(userIdentifier, "approve", reason, null);
    }

    @Override
    public synchronized AuthenticatedUser rejectRegistration(
            String userIdentifier, String reason) {
        return adminAction(userIdentifier, "reject", reason, null);
    }

    @Override
    public synchronized AuthenticatedUser activateAdminUser(
            String userIdentifier, String reason) {
        return adminAction(userIdentifier, "activate", reason, null);
    }

    @Override
    public synchronized AuthenticatedUser suspendAdminUser(
            String userIdentifier, String reason) {
        return adminAction(userIdentifier, "suspend", reason, null);
    }

    @Override
    public synchronized AuthenticatedUser disableAdminUser(
            String userIdentifier, String reason) {
        return adminAction(userIdentifier, "disable", reason, null);
    }

    @Override
    public synchronized AuthenticatedUser grantAdminRole(
            String userIdentifier, char[] ownerPassword, String reason) {
        return adminAction(userIdentifier, "grant-admin", reason,
                ownerPassword);
    }

    @Override
    public synchronized AuthenticatedUser revokeAdminRole(
            String userIdentifier, char[] ownerPassword, String reason) {
        return adminAction(userIdentifier, "revoke-admin", reason,
                ownerPassword);
    }

    @Override
    public synchronized AdminApplicationSettings updateAdminApplicationSettings(
            boolean approvalRequired, char[] ownerPassword, String reason) {
        String password = ownerPassword == null ? ""
                : new String(ownerPassword);
        try {
            String json = send("PUT", "/api/admin/settings", "{"
                    + "\"registrationRequiresAdminApproval\":"
                    + approvalRequired + ","
                    + field("currentPassword", password) + ","
                    + field("reason", required(reason, "Reason")) + "}",
                    adminToken());
            return new AdminApplicationSettings(bool(json,
                    "registrationRequiresAdminApproval"), true);
        } finally {
            password = null;
        }
    }

    @Override
    public boolean isConfigured() {
        return configuration.isConfigured();
    }

    private String adminGet(String path) {
        return send("GET", path, "", adminToken());
    }

    private AuthenticatedUser adminAction(
            String userIdentifier, String action,
            String reason, char[] currentPassword) {
        String password = currentPassword == null ? ""
                : new String(currentPassword);
        try {
            String body = "{" + field("reason", required(reason, "Reason"))
                    + (currentPassword == null ? "" : ","
                    + field("currentPassword", password)) + "}";
            return user(send("POST", "/api/admin/users/"
                    + required(userIdentifier, "User ID") + "/" + action,
                    body, adminToken()));
        } finally {
            password = null;
        }
    }

    private String adminToken() {
        return requireToken(accessToken,
                "No online administrator session is active.");
    }

    private static List<AuthenticatedUser> users(String json) {
        return objects(json).stream().map(HttpRegistrationGateway::user)
                .toList();
    }

    private static List<String> objects(String json) {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^{}]*)}").matcher(
                json == null ? "" : json);
        while (matcher.find()) result.add(matcher.group());
        return List.copyOf(result);
    }

    private String post(String path, String body) {
        return send("POST", path, body, null);
    }

    private String send(
            String method, String path, String body, String bearerToken) {
        return send(method, path, body, bearerToken, REQUEST_TIMEOUT);
    }

    private String send(
            String method, String path, String body, String bearerToken,
            Duration timeout) {
        URI target = URI.create(configuration.requireBaseUri() + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=UTF-8");
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpRequest request = switch (method) {
            case "GET" -> builder.GET().build();
            case "DELETE" -> builder.DELETE().build();
            case "PUT" -> builder.PUT(
                    HttpRequest.BodyPublishers.ofString(body)).build();
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
        AuthProvider provider = AuthProvider.valueOf(required(
                string(json, "primaryAuthProvider"),
                "Authentication provider"));
        String googleSubject = optional(
                string(json, "googleSubjectId"));
        return new AuthenticatedUser(identifier, fullName, email, verified,
                provider, googleSubject, status, created, updated, lastLogin,
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

    private static double number(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
                .matcher(json == null ? "" : json);
        if (!matcher.find()) {
            throw new AuthException(name + " is missing from the server response.");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static int integer(String json, String name) {
        return (int) number(json, name);
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

    private static void sleepBeforePoll() {
        try {
            Thread.sleep(750);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthException("Google Sign-In was interrupted.", exception);
        }
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
