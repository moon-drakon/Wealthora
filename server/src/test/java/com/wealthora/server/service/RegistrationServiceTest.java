package com.wealthora.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.RegisterRequest;
import com.wealthora.server.api.UserResponse;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.PasswordPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "dev-mail-sink"})
class RegistrationServiceTest {

    private static final Path MAIL_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "wealthora-test-mail");

    @Autowired private RegistrationService registrationService;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private EmailVerificationRepository verifications;
    @Autowired private AuthenticationIdentityRepository identities;
    @Autowired private UserRoleRepository roles;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordPolicy passwordPolicy;

    @BeforeEach
    void reset() throws IOException {
        auditLogs.deleteAll();
        verifications.deleteAll();
        identities.deleteAll();
        roles.deleteAll();
        users.deleteAll();
        Files.createDirectories(MAIL_DIRECTORY);
        try (var files = Files.list(MAIL_DIRECTORY)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    @Test
    void registrationVerificationAndApprovalPolicy() throws Exception {
        UserResponse pending = registrationService.register(request(
                "New Student", " New.Student@NorthSouth.edu "));
        assertEquals("new.student@northsouth.edu", pending.email());
        assertFalse(pending.emailVerified());
        assertEquals("PENDING_EMAIL_VERIFICATION", pending.accountStatus());
        assertEquals(java.util.Set.of("USER"), pending.roles());

        Path delivered = MAIL_DIRECTORY.resolve(
                "new.student_northsouth.edu.txt");
        assertTrue(Files.isRegularFile(delivered));
        String code = Files.readAllLines(delivered).stream()
                .filter(line -> line.startsWith("code="))
                .findFirst().orElseThrow().substring(5);
        assertEquals(6, code.length());

        assertThrows(ApiException.class, () -> registrationService.verify(
                pending.email(), "000000"));
        assertEquals(1, verifications
                .findFirstByUserIdOrderBySentAtDesc(
                        users.findByEmail(pending.email()).orElseThrow().getId())
                .orElseThrow().getFailedAttempts());
        UserResponse verified = registrationService.verify(
                pending.email(), code);
        assertTrue(verified.emailVerified());
        assertEquals("PENDING_APPROVAL", verified.accountStatus());
        assertTrue(auditLogs.findAll().stream().anyMatch(entry -> true));
    }

    @Test
    void exactNsuDomainDuplicateAndStrongPasswordRules() {
        assertThrows(ApiException.class, () -> registrationService.register(
                request("Student", "student@gmail.com")));
        assertThrows(ApiException.class, () -> registrationService.register(
                request("Student", "student@northsouth.edu.example.com")));
        assertThrows(ApiException.class, () -> registrationService.register(
                new RegisterRequest("Student", "student@northsouth.edu", "",
                        "weak-password".toCharArray(),
                        "weak-password".toCharArray(), true)));

        registrationService.register(request(
                "Student", "student@northsouth.edu"));
        ApiException duplicate = assertThrows(ApiException.class,
                () -> registrationService.register(request(
                        "Student Again", "student@northsouth.edu")));
        assertEquals("EMAIL_ALREADY_REGISTERED", duplicate.getCode());
    }

    @Test
    void requestedPasswordPolicyExamplesAreEnforced() {
        passwordPolicy.requireStrong("moon1234".toCharArray());
        passwordPolicy.requireStrong("wealthora25".toCharArray());
        passwordPolicy.requireStrong("student2026".toCharArray());
        passwordPolicy.requireStrong(
                ("a1" + "x".repeat(126)).toCharArray());
        assertThrows(ApiException.class,
                () -> passwordPolicy.requireStrong("12345678".toCharArray()));
        assertThrows(ApiException.class,
                () -> passwordPolicy.requireStrong("abcdefgh".toCharArray()));
        assertThrows(ApiException.class,
                () -> passwordPolicy.requireStrong("moon12".toCharArray()));
        assertThrows(ApiException.class,
                () -> passwordPolicy.requireStrong(" moon1234".toCharArray()));
        assertThrows(ApiException.class,
                () -> passwordPolicy.requireStrong("moon1234 ".toCharArray()));
        assertThrows(ApiException.class, () -> passwordPolicy.requireStrong(
                ("a1" + "x".repeat(127)).toCharArray()));
    }

    @Test
    void resendCooldownAndTermsAreEnforced() {
        assertThrows(ApiException.class, () -> registrationService.register(
                new RegisterRequest("Student", "student@northsouth.edu", "",
                        "ValidStudent1!".toCharArray(),
                        "ValidStudent1!".toCharArray(), false)));
        registrationService.register(request(
                "Student", "student@northsouth.edu"));
        ApiException cooldown = assertThrows(ApiException.class,
                () -> registrationService.resend(
                        "student@northsouth.edu"));
        assertEquals("RESEND_COOLDOWN", cooldown.getCode());
    }

    private static RegisterRequest request(String name, String email) {
        return new RegisterRequest(name, email, "2530000000",
                "ValidStudent1!".toCharArray(),
                "ValidStudent1!".toCharArray(), true);
    }
}
