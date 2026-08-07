package com.wealthora.server.service;

import com.wealthora.server.config.OwnerBootstrapProperties;
import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuditLogEntry;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRole;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.NsuEmailPolicy;
import com.wealthora.server.security.PasswordPolicy;
import java.nio.CharBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OwnerBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            OwnerBootstrapRunner.class);

    private final OwnerBootstrapProperties properties;
    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final UserRoleRepository roles;
    private final AuditLogRepository auditLogs;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public OwnerBootstrapRunner(
            OwnerBootstrapProperties properties,
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            UserRoleRepository roles,
            AuditLogRepository auditLogs,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.auditLogs = auditLogs;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (roles.findAll().stream().anyMatch(assignment ->
                UserRole.OWNER.name().equals(assignment.getRoleName()))) {
            return;
        }
        if (properties.email() == null || properties.email().isBlank()) {
            return;
        }

        String email = NsuEmailPolicy.require(properties.email());
        if (users.existsByEmail(email)) {
            LOGGER.warn("The configured initial OWNER account already exists. "
                    + "Automatic promotion is disabled; use the one-time "
                    + "OWNER recovery claim flow.");
            return;
        }
        if (!properties.isCreationComplete()) {
            LOGGER.warn("Initial OWNER creation is incomplete. The server will "
                    + "start without creating or changing an account.");
            return;
        }
        String fullName = properties.fullName().strip();
        if (fullName.length() > 160) {
            throw new IllegalStateException(
                    "The configured OWNER name must not exceed 160 characters.");
        }
        char[] password = properties.password().toCharArray();
        passwordPolicy.requireStrong(password);
        String passwordText = CharBuffer.wrap(password).toString();
        try {
            Instant now = clock.instant();
            UUID userId = UUID.randomUUID();
            UserAccount owner = new UserAccount(userId,
                    fullName, email, null,
                    AccountStatus.ACTIVE, now);
            owner.verifyEmail(AccountStatus.ACTIVE, now);
            users.save(owner);
            identities.save(new AuthenticationIdentity(UUID.randomUUID(),
                    userId, AuthProvider.PASSWORD,
                    passwordEncoder.encode(passwordText), now));
            roles.save(new UserRoleAssignment(userId, UserRole.USER));
            roles.save(new UserRoleAssignment(userId, UserRole.ADMIN));
            roles.save(new UserRoleAssignment(userId, UserRole.OWNER));
            auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now, userId,
                    "OWNER_BOOTSTRAPPED", userId, "SUCCESS",
                    "Initial protected OWNER account created."));
        } finally {
            passwordText = null;
            Arrays.fill(password, '\0');
        }
    }
}
