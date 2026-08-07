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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OwnerBootstrapRunner implements ApplicationRunner {

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
        if (!properties.hasAnyValue()) {
            return;
        }
        if (!properties.isComplete()) {
            throw new IllegalStateException(
                    "OWNER bootstrap requires name, email, and password together.");
        }

        String email = NsuEmailPolicy.require(properties.email());
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
            UserAccount existing = users.findByEmail(email).orElse(null);
            if (existing != null) {
                promoteExistingAccount(existing, passwordText, now);
                return;
            }
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

    private void promoteExistingAccount(
            UserAccount account, String configuredPassword, Instant now) {
        if (account.getAccountStatus() != AccountStatus.ACTIVE
                || !account.isEmailVerified()) {
            throw new IllegalStateException(
                    "The configured existing OWNER account must be active and verified.");
        }
        AuthenticationIdentity passwordIdentity = identities
                .findByUserIdAndProvider(account.getId(), AuthProvider.PASSWORD)
                .orElseThrow(() -> new IllegalStateException(
                        "The configured existing OWNER account has no password identity."));
        if (passwordIdentity.getPasswordHash() == null
                || !passwordEncoder.matches(configuredPassword,
                        passwordIdentity.getPasswordHash())) {
            throw new IllegalStateException(
                    "The configured OWNER password does not match the existing account.");
        }

        grantRoleIfMissing(account.getId(), UserRole.USER);
        grantRoleIfMissing(account.getId(), UserRole.ADMIN);
        grantRoleIfMissing(account.getId(), UserRole.OWNER);
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                account.getId(), "OWNER_BOOTSTRAPPED", account.getId(),
                "SUCCESS",
                "Existing verified account promoted to protected OWNER."));
    }

    private void grantRoleIfMissing(UUID userId, UserRole role) {
        if (!roles.existsByUserIdAndRoleName(userId, role.name())) {
            roles.save(new UserRoleAssignment(userId, role));
        }
    }
}
