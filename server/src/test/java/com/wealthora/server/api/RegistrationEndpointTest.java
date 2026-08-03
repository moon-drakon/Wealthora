package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev-mail-sink"})
class RegistrationEndpointTest {

    private static final Path MAIL_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "wealthora-test-mail");

    @LocalServerPort private int port;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private EmailVerificationRepository verifications;
    @Autowired private AuthenticationIdentityRepository identities;
    @Autowired private UserRoleRepository roles;
    @Autowired private UserAccountRepository users;

    @BeforeEach
    void reset() throws Exception {
        auditLogs.deleteAll();
        verifications.deleteAll();
        identities.deleteAll();
        roles.deleteAll();
        users.deleteAll();
        Files.createDirectories(MAIL_DIRECTORY);
        Files.deleteIfExists(MAIL_DIRECTORY.resolve(
                "endpoint.student_northsouth.edu.txt"));
    }

    @Test
    void httpRegistrationAndVerificationNeverReturnSecrets() throws Exception {
        String registration = "{"
                + "\"fullName\":\"Endpoint Student\","
                + "\"email\":\"endpoint.student@northsouth.edu\","
                + "\"studentId\":\"2530000001\","
                + "\"password\":\"EndpointStudent1!\","
                + "\"passwordConfirmation\":\"EndpointStudent1!\","
                + "\"termsAccepted\":true}";
        HttpResponse<String> created = post(
                "/api/auth/register", registration);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("PENDING_EMAIL_VERIFICATION"));
        assertFalse(created.body().contains("EndpointStudent1!"));
        assertFalse(created.body().contains("passwordHash"));

        String code = Files.readAllLines(MAIL_DIRECTORY.resolve(
                "endpoint.student_northsouth.edu.txt")).stream()
                .filter(line -> line.startsWith("code="))
                .findFirst().orElseThrow().substring(5);
        HttpResponse<String> verified = post("/api/auth/verify-email",
                "{\"email\":\"endpoint.student@northsouth.edu\","
                        + "\"code\":\"" + code + "\"}");
        assertEquals(200, verified.statusCode());
        assertTrue(verified.body().contains("PENDING_APPROVAL"));
        assertFalse(verified.body().contains(code));
    }

    private HttpResponse<String> post(String path, String json)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
    }
}
