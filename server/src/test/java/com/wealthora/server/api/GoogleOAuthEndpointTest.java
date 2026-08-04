package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.oauth.GoogleIdentityGateway;
import com.wealthora.server.oauth.VerifiedGoogleIdentity;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
import com.wealthora.server.repository.GoogleOAuthFlowRepository;
import com.wealthora.server.repository.LoginAttemptRepository;
import com.wealthora.server.repository.PasswordResetTokenRepository;
import com.wealthora.server.repository.RefreshTokenRepository;
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "wealthora.registration.requires-admin-approval=false")
@ActiveProfiles({"test", "dev-mail-sink"})
@Import(GoogleOAuthEndpointTest.GoogleTestConfiguration.class)
class GoogleOAuthEndpointTest {

    private static final String EMAIL = "oauth.student@northsouth.edu";
    private static final String PASSWORD = "OAuthStudent1!";
    private static final Path MAIL_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "wealthora-test-mail");

    @LocalServerPort private int port;
    @Autowired private GoogleOAuthFlowRepository googleFlows;
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
        deleteData();
        Files.createDirectories(MAIL_DIRECTORY);
        Files.deleteIfExists(mailFile());
        createActivePasswordAccount();
    }

    @AfterEach
    void cleanup() {
        deleteData();
    }

    @Test
    void existingPasswordAccountLinksAndFlowIsSingleUse() throws Exception {
        HttpResponse<String> status = get("/api/auth/google/status");
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"configured\":true"));
        Flow flow = start();
        assertTrue(flow.authorizationUrl().contains("hd=northsouth.edu"));
        assertTrue(flow.authorizationUrl().contains("nonce="));

        HttpResponse<String> callback = callback(flow, "existing");
        assertEquals(200, callback.statusCode());
        assertTrue(callback.body().contains("Google sign-in completed"));
        HttpResponse<String> polled = poll(flow);
        assertEquals(200, polled.statusCode());
        assertTrue(polled.body().contains("\"status\":\"COMPLETED\""));
        assertTrue(polled.body().contains(
                "\"primaryAuthProvider\":\"LOCAL_AND_GOOGLE\""));
        assertTrue(polled.body().contains("\"googleSubjectId\":\"sub-existing\""));
        assertEquals(1, users.count());
        UserAccount user = users.findByEmail(EMAIL).orElseThrow();
        assertTrue(identities.findByUserIdAndProvider(
                user.getId(), AuthProvider.PASSWORD).isPresent());
        assertTrue(identities.findByUserIdAndProvider(
                user.getId(), AuthProvider.GOOGLE).isPresent());
        assertEquals(409, poll(flow).statusCode());
    }

    @Test
    void googleFirstUserCanSetPasswordWithoutCreatingDuplicate() throws Exception {
        Flow flow = start();
        assertEquals(200, callback(flow, "new-account").statusCode());
        HttpResponse<String> polled = poll(flow);
        String access = string(polled.body(), "accessToken");
        assertTrue(polled.body().contains(
                "\"primaryAuthProvider\":\"GOOGLE\""));
        String newPassword = "GoogleFirst2!";
        String body = "{\"newPassword\":\"" + newPassword + "\","
                + "\"passwordConfirmation\":\"" + newPassword + "\"}";
        assertEquals(204, postAuthorized(
                "/api/auth/set-password", body, access).statusCode());
        HttpResponse<String> login = post("/api/auth/login", "{"
                + "\"email\":\"google.new@northsouth.edu\","
                + "\"password\":\"" + newPassword + "\","
                + "\"deviceLabel\":\"OAuth test\"}");
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains(
                "\"primaryAuthProvider\":\"LOCAL_AND_GOOGLE\""));
        assertEquals(2, users.count());
    }

    @Test
    void wrongDomainAndTamperedFlowAreRejected() throws Exception {
        Flow flow = start();
        assertEquals(400, get("/api/auth/google/callback?state=tampered&code=x")
                .statusCode());
        assertEquals(401, post("/api/auth/google/poll", "{"
                + "\"flowIdentifier\":\"" + flow.identifier() + "\","
                + "\"pollSecret\":\"wrong-secret-with-more-than-32-characters\"}")
                .statusCode());
        HttpResponse<String> callback = callback(flow, "wrong-domain");
        assertEquals(200, callback.statusCode());
        assertTrue(callback.body().contains("not completed"));
        HttpResponse<String> polled = poll(flow);
        assertTrue(polled.body().contains("\"status\":\"FAILED\""));
        assertTrue(polled.body().contains("northsouth.edu"));
        assertEquals(1, users.count());
    }

    @Test
    void suspendedAccountCannotLinkOrSignIn() throws Exception {
        UserAccount user = users.findByEmail(EMAIL).orElseThrow();
        user.changeStatus(AccountStatus.SUSPENDED, Instant.now());
        users.save(user);
        Flow flow = start();
        assertEquals(200, callback(flow, "existing").statusCode());
        HttpResponse<String> polled = poll(flow);
        assertTrue(polled.body().contains("\"status\":\"FAILED\""));
        assertTrue(identities.findByUserIdAndProvider(
                user.getId(), AuthProvider.GOOGLE).isEmpty());
    }

    @Test
    void invalidIdentityClaimsAreRejectedIndependently() throws Exception {
        for (String code : List.of("bad-issuer", "bad-audience", "expired",
                "unverified", "bad-nonce", "missing-subject")) {
            Flow flow = start();
            assertEquals(200, callback(flow, code).statusCode());
            HttpResponse<String> polled = poll(flow);
            assertEquals(200, polled.statusCode());
            assertTrue(polled.body().contains("\"status\":\"FAILED\""));
        }
        UserAccount user = users.findByEmail(EMAIL).orElseThrow();
        assertTrue(identities.findByUserIdAndProvider(
                user.getId(), AuthProvider.GOOGLE).isEmpty());
        assertEquals(1, users.count());
    }

    @Test
    void linkedSubjectCannotMoveToAnotherEmail() throws Exception {
        Flow first = start();
        assertEquals(200, callback(first, "existing").statusCode());
        assertTrue(poll(first).body().contains("\"status\":\"COMPLETED\""));

        Flow second = start();
        assertEquals(200, callback(
                second, "subject-email-mismatch").statusCode());
        assertTrue(poll(second).body().contains("\"status\":\"FAILED\""));
        assertEquals(1, users.count());
        assertEquals(2, identities.count());
    }

    @Test
    void userCannotAcquireASecondGoogleSubject() throws Exception {
        UserAccount user = users.findByEmail(EMAIL).orElseThrow();
        identities.save(new AuthenticationIdentity(UUID.randomUUID(),
                user.getId(), AuthProvider.GOOGLE,
                "already-linked-subject", null, Instant.now()));

        Flow flow = start();
        assertEquals(200, callback(flow, "existing").statusCode());
        assertTrue(poll(flow).body().contains("\"status\":\"FAILED\""));
        assertEquals(1, users.count());
        assertEquals(2, identities.count());
    }

    private Flow start() throws Exception {
        HttpResponse<String> response = post("/api/auth/google/start",
                "{\"deviceLabel\":\"OAuth endpoint test\"}");
        assertEquals(200, response.statusCode());
        return new Flow(string(response.body(), "flowIdentifier"),
                string(response.body(), "pollSecret"),
                string(response.body(), "authorizationUrl"));
    }

    private HttpResponse<String> callback(Flow flow, String code)
            throws Exception {
        String state = parameter(flow.authorizationUrl(), "state");
        return get("/api/auth/google/callback?state=" + encode(state)
                + "&code=" + encode(code));
    }

    private HttpResponse<String> poll(Flow flow) throws Exception {
        return post("/api/auth/google/poll", "{"
                + "\"flowIdentifier\":\"" + flow.identifier() + "\","
                + "\"pollSecret\":\"" + flow.pollSecret() + "\"}");
    }

    private void createActivePasswordAccount() throws Exception {
        post("/api/auth/register", "{"
                + "\"fullName\":\"OAuth Student\","
                + "\"email\":\"" + EMAIL + "\","
                + "\"studentId\":\"2530000004\","
                + "\"password\":\"" + PASSWORD + "\","
                + "\"passwordConfirmation\":\"" + PASSWORD + "\","
                + "\"termsAccepted\":true}");
        String code = Files.readAllLines(mailFile()).stream()
                .filter(line -> line.startsWith("code="))
                .findFirst().orElseThrow().substring(5);
        post("/api/auth/verify-email", "{\"email\":\"" + EMAIL
                + "\",\"code\":\"" + code + "\"}");
    }

    private void deleteData() {
        googleFlows.deleteAll();
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

    private Path mailFile() {
        return MAIL_DIRECTORY.resolve("oauth.student_northsouth.edu.txt");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private HttpResponse<String> post(String path, String body)
            throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpResponse<String> postAuthorized(
            String path, String body, String access) throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + access)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
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
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                .matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + name);
        return matcher.group(1).replace("\\/", "/")
                .replace("\\u0026", "&");
    }

    private static String parameter(String url, String name) {
        Matcher matcher = Pattern.compile("(?:[?&])" + name + "=([^&]+)")
                .matcher(url);
        if (!matcher.find()) throw new AssertionError("Missing " + name);
        return java.net.URLDecoder.decode(
                matcher.group(1), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Flow(
            String identifier, String pollSecret, String authorizationUrl) {
    }

    @TestConfiguration
    static class GoogleTestConfiguration {

        @Bean
        @Primary
        FakeGoogleIdentityGateway fakeGoogleIdentityGateway() {
            return new FakeGoogleIdentityGateway();
        }
    }

    static final class FakeGoogleIdentityGateway
            implements GoogleIdentityGateway {

        private final AtomicReference<String> nonce = new AtomicReference<>();

        @Override public boolean isConfigured() { return true; }
        @Override public String configurationMessage() {
            return "Test Google OAuth is ready.";
        }
        @Override public String redirectUri() {
            return "http://127.0.0.1:9999/api/auth/google/callback";
        }
        @Override public String authorizationUrl(String state, String value) {
            nonce.set(value);
            return "https://accounts.google.com/o/oauth2/v2/auth?state="
                    + encode(state) + "&nonce=" + encode(value)
                    + "&hd=northsouth.edu";
        }
        @Override public VerifiedGoogleIdentity exchangeAndVerify(String code) {
            String email = switch (code) {
                case "new-account" -> "google.new@northsouth.edu";
                case "wrong-domain" -> "oauth.student@gmail.com";
                case "subject-email-mismatch" ->
                    "oauth.other@northsouth.edu";
                default -> EMAIL;
            };
            String domain = code.equals("wrong-domain")
                    ? "gmail.com" : "northsouth.edu";
            String subject = switch (code) {
                case "new-account" -> "sub-new";
                case "missing-subject" -> "";
                default -> "sub-existing";
            };
            String issuer = code.equals("bad-issuer")
                    ? "https://identity.example" : "https://accounts.google.com";
            List<String> audience = code.equals("bad-audience")
                    ? List.of("another-client.apps.googleusercontent.com")
                    : List.of("test-google-client.apps.googleusercontent.com");
            Instant expiry = code.equals("expired")
                    ? Instant.now().minusSeconds(1)
                    : Instant.now().plusSeconds(600);
            String returnedNonce = code.equals("bad-nonce")
                    ? "incorrect-nonce" : nonce.get();
            return new VerifiedGoogleIdentity(subject, email,
                    !code.equals("unverified"), domain,
                    "Google Student", returnedNonce, issuer,
                    audience, expiry);
        }
    }
}
