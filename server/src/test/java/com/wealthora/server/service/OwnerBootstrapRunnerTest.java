package com.wealthora.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.UserRole;
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
import org.junit.jupiter.api.AfterAll;
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

    private static ApplicationSettingRepository settings;
    private static GoogleOAuthFlowRepository googleFlows;
    private static RefreshTokenRepository refreshTokens;
    private static SessionRecordRepository sessions;
    private static LoginAttemptRepository loginAttempts;
    private static PasswordResetTokenRepository passwordResetTokens;
    private static AuditLogRepository auditLogs;
    private static EmailVerificationRepository verifications;
    private static AuthenticationIdentityRepository identities;
    private static UserRoleRepository roles;
    private static UserAccountRepository users;

    @Autowired
    void repositories(
            ApplicationSettingRepository applicationSettings,
            GoogleOAuthFlowRepository oauthFlows,
            RefreshTokenRepository refreshTokenRepository,
            SessionRecordRepository sessionRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordResetTokenRepository resetTokenRepository,
            AuditLogRepository auditLogRepository,
            EmailVerificationRepository verificationRepository,
            AuthenticationIdentityRepository identityRepository,
            UserRoleRepository roleRepository,
            UserAccountRepository userRepository) {
        settings = applicationSettings;
        googleFlows = oauthFlows;
        refreshTokens = refreshTokenRepository;
        sessions = sessionRepository;
        loginAttempts = loginAttemptRepository;
        passwordResetTokens = resetTokenRepository;
        auditLogs = auditLogRepository;
        verifications = verificationRepository;
        identities = identityRepository;
        roles = roleRepository;
        users = userRepository;
    }

    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void createsProtectedOwnerOnlyThroughServerConfiguration() {
        var owner = users.findByEmail("bootstrap.owner@northsouth.edu")
                .orElseThrow();
        assertEquals(AccountStatus.ACTIVE, owner.getAccountStatus());
        assertTrue(owner.isEmailVerified());
        assertTrue(roles.existsByUserIdAndRoleName(
                owner.getId(), UserRole.USER.name()));
        assertTrue(roles.existsByUserIdAndRoleName(
                owner.getId(), UserRole.ADMIN.name()));
        assertTrue(roles.existsByUserIdAndRoleName(
                owner.getId(), UserRole.OWNER.name()));
        String hash = identities.findByUserIdAndProvider(
                owner.getId(), AuthProvider.PASSWORD).orElseThrow()
                .getPasswordHash();
        assertTrue(passwordEncoder.matches("BootstrapOwner1!", hash));
        assertEquals(1, auditLogs.findAll().stream().filter(event ->
                "OWNER_BOOTSTRAPPED".equals(event.getAction())).count());
    }

    @AfterAll
    static void cleanup() {
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
