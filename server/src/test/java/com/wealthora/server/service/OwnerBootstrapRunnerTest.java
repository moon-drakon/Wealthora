package com.wealthora.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRole;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.repository.ApplicationSettingRepository;
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
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "wealthora.owner-bootstrap.full-name=Bootstrap Owner",
            "wealthora.owner-bootstrap.email=bootstrap.owner@northsouth.edu",
            "wealthora.owner-bootstrap.password=BootstrapOwner1!"
        })
@ActiveProfiles("test")
class OwnerBootstrapRunnerTest {

    @Autowired private ApplicationSettingRepository settings;
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OwnerBootstrapRunner runner;

    @BeforeEach
    void setup() {
        deleteData();
    }

    @AfterEach
    void cleanup() {
        deleteData();
    }

    @Test
    void createsProtectedOwnerOnlyThroughServerConfiguration() {
        runner.run(null);

        UserAccount owner = users.findByEmail(
                "bootstrap.owner@northsouth.edu").orElseThrow();
        assertProtectedOwner(owner);
        String hash = identities.findByUserIdAndProvider(
                owner.getId(), AuthProvider.PASSWORD).orElseThrow()
                .getPasswordHash();
        assertTrue(passwordEncoder.matches("BootstrapOwner1!", hash));
        assertEquals(1, ownerAuditCount());
    }

    @Test
    void promotesMatchingActiveVerifiedAccountWithoutChangingPassword() {
        Instant now = Instant.parse("2025-01-08T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UserAccount existing = new UserAccount(userId, "Bootstrap Owner",
                "bootstrap.owner@northsouth.edu", null,
                AccountStatus.ACTIVE, now);
        existing.verifyEmail(AccountStatus.ACTIVE, now);
        users.save(existing);
        String originalHash = passwordEncoder.encode("BootstrapOwner1!");
        identities.save(new AuthenticationIdentity(UUID.randomUUID(), userId,
                AuthProvider.PASSWORD, originalHash, now));
        roles.save(new UserRoleAssignment(userId, UserRole.USER));

        runner.run(null);

        assertProtectedOwner(existing);
        assertEquals(originalHash, identities.findByUserIdAndProvider(
                userId, AuthProvider.PASSWORD).orElseThrow()
                .getPasswordHash());
        assertEquals(1, ownerAuditCount());
    }

    @Test
    void rejectsExistingAccountWhenConfiguredPasswordDoesNotMatch() {
        Instant now = Instant.parse("2025-01-08T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UserAccount existing = new UserAccount(userId, "Bootstrap Owner",
                "bootstrap.owner@northsouth.edu", null,
                AccountStatus.ACTIVE, now);
        existing.verifyEmail(AccountStatus.ACTIVE, now);
        users.save(existing);
        identities.save(new AuthenticationIdentity(UUID.randomUUID(), userId,
                AuthProvider.PASSWORD,
                passwordEncoder.encode("DifferentPassword1!"), now));
        roles.save(new UserRoleAssignment(userId, UserRole.USER));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runner.run(null));

        assertEquals("The configured OWNER password does not match the existing account.",
                error.getMessage());
        assertFalse(roles.existsByUserIdAndRoleName(
                userId, UserRole.ADMIN.name()));
        assertFalse(roles.existsByUserIdAndRoleName(
                userId, UserRole.OWNER.name()));
        assertEquals(0, ownerAuditCount());
    }

    private void assertProtectedOwner(UserAccount owner) {
        assertEquals(AccountStatus.ACTIVE, owner.getAccountStatus());
        assertTrue(owner.isEmailVerified());
        assertTrue(roles.existsByUserIdAndRoleName(
                owner.getId(), UserRole.USER.name()));
        assertTrue(roles.existsByUserIdAndRoleName(
                owner.getId(), UserRole.ADMIN.name()));
        assertTrue(roles.existsByUserIdAndRoleName(
                owner.getId(), UserRole.OWNER.name()));
    }

    private long ownerAuditCount() {
        return auditLogs.findAll().stream().filter(event ->
                "OWNER_BOOTSTRAPPED".equals(event.getAction())).count();
    }

    private void deleteData() {
        settings.deleteAll();
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
}
