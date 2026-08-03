package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
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
import com.wealthora.server.speech.CloudSpeechResult;
import com.wealthora.server.speech.SpeechBackendStatus;
import com.wealthora.server.speech.SpeechRecognitionGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev-mail-sink"})
@Import(SpeechEndpointTest.SpeechTestConfiguration.class)
class SpeechEndpointTest {

    private static final String EMAIL = "speech.student@northsouth.edu";
    private static final String PASSWORD = "SpeechStudent1!";
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
        deleteData();
        Files.createDirectories(MAIL_DIRECTORY);
        Files.deleteIfExists(mailFile());
        createActiveAccount();
    }

    @AfterEach
    void cleanup() {
        deleteData();
    }

    @Test
    void speechEndpointsRequireAuthentication() throws Exception {
        assertEquals(401, get("/api/speech/status", null).statusCode());
        assertEquals(401, post("/api/speech/recognize", "{}", null)
                .statusCode());
    }

    @Test
    void authenticatedSessionCanCheckStatusAndRecognizePcm() throws Exception {
        String access = login();
        HttpResponse<String> status = get("/api/speech/status", access);
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"apiVersion\":\"V1\""));
        assertTrue(status.body().contains("\"ready\":true"));

        String audio = Base64.getEncoder().encodeToString(new byte[3_200]);
        HttpResponse<String> response = post("/api/speech/recognize", "{"
                + "\"audioBase64\":\"" + audio + "\","
                + "\"sampleRateHertz\":16000,"
                + "\"language\":\"AUTOMATIC\"}", access);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Paid 500 taka for lunch"));
        assertTrue(response.body().contains("\"confidence\":0.91"));
        assertTrue(response.body().contains("\"detectedLocale\":\"en-US\""));
        assertTrue(response.body().contains(
                "\"audioDurationMilliseconds\":100"));
    }

    @Test
    void invalidAudioIsRejectedBeforeProviderCall() throws Exception {
        String access = login();
        assertEquals(400, post("/api/speech/recognize", "{"
                + "\"audioBase64\":\"not-base64!\","
                + "\"sampleRateHertz\":16000,"
                + "\"language\":\"ENGLISH\"}", access).statusCode());
        String shortAudio = Base64.getEncoder().encodeToString(new byte[10]);
        assertEquals(400, post("/api/speech/recognize", "{"
                + "\"audioBase64\":\"" + shortAudio + "\","
                + "\"sampleRateHertz\":16000,"
                + "\"language\":\"BANGLA\"}", access).statusCode());
    }

    private String login() throws Exception {
        HttpResponse<String> response = post("/api/auth/login", "{"
                + "\"email\":\"" + EMAIL + "\","
                + "\"password\":\"" + PASSWORD + "\","
                + "\"deviceLabel\":\"Speech endpoint test\"}", null);
        assertEquals(200, response.statusCode());
        return string(response.body(), "accessToken");
    }

    private void createActiveAccount() throws Exception {
        post("/api/auth/register", "{"
                + "\"fullName\":\"Speech Student\","
                + "\"email\":\"" + EMAIL + "\","
                + "\"studentId\":\"2530000003\","
                + "\"password\":\"" + PASSWORD + "\","
                + "\"passwordConfirmation\":\"" + PASSWORD + "\","
                + "\"termsAccepted\":true}", null);
        String code = Files.readAllLines(mailFile()).stream()
                .filter(line -> line.startsWith("code="))
                .findFirst().orElseThrow().substring(5);
        post("/api/auth/verify-email", "{\"email\":\"" + EMAIL
                + "\",\"code\":\"" + code + "\"}", null);
        UserAccount user = users.findByEmail(EMAIL).orElseThrow();
        user.changeStatus(AccountStatus.ACTIVE, Instant.now());
        users.save(user);
    }

    private void deleteData() {
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
        return MAIL_DIRECTORY.resolve("speech.student_northsouth.edu.txt");
    }

    private HttpResponse<String> get(String path, String accessToken)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Accept", "application/json");
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return send(builder.GET().build());
    }

    private HttpResponse<String> post(
            String path, String json, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json");
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return send(builder.POST(
                HttpRequest.BodyPublishers.ofString(json)).build());
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
                + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + name);
        return matcher.group(1);
    }

    @TestConfiguration
    static class SpeechTestConfiguration {

        @Bean
        @Primary
        SpeechRecognitionGateway testSpeechGateway() {
            return new SpeechRecognitionGateway() {
                @Override
                public SpeechBackendStatus status(String projectId) {
                    return new SpeechBackendStatus(true,
                            "Test speech provider is ready.");
                }

                @Override
                public CloudSpeechResult recognize(
                        String projectId, byte[] linearPcmAudio,
                        int sampleRateHertz, String primaryLocale,
                        List<String> alternativeLocales) {
                    return new CloudSpeechResult(
                            "Paid 500 taka for lunch", 0.91, "en-US");
                }
            };
        }
    }
}
