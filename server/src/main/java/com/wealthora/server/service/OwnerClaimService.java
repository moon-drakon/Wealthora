package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.ResetPasswordRequest;
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
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.NsuEmailPolicy;
import com.wealthora.server.security.PasswordPolicy;
import com.wealthora.server.security.TokenHasher;
import java.nio.CharBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles the explicit, one-time recovery claim for a pre-existing initial
 * OWNER account. Merely configuring an email never grants a role.
 */
@Service
public class OwnerClaimService {

    private final OwnerBootstrapProperties properties;
    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final UserRoleRepository roles;
    private final SessionRecordRepository sessions;
    private final AuditLogRepository auditLogs;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;

    public OwnerClaimService(
            OwnerBootstrapProperties properties,
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            UserRoleRepository roles,
            SessionRecordRepository sessions,
            AuditLogRepository auditLogs,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            TokenHasher tokenHasher) {
        this.properties = properties;
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.sessions = sessions;
        this.auditLogs = auditLogs;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
    }

    public boolean tryClaim(
            String email, ResetPasswordRequest request, Instant now) {
        if (!properties.isClaimComplete()
                || !email.equals(configuredEmail())
                || !claimTokenMatches(request.resetToken())) {
            return false;
        }

        UserAccount account = users.findByEmailForUpdate(email).orElse(null);
        if (account == null || account.getAccountStatus() != AccountStatus.ACTIVE
                || !account.isEmailVerified() || ownerExists()) {
            return false;
        }
        AuthenticationIdentity identity = identities
                .findByUserIdAndProvider(account.getId(), AuthProvider.PASSWORD)
                .orElse(null);
        if (identity == null || identity.getPasswordHash() == null) {
            return false;
        }

        requireMatchingStrongPasswords(
                request.newPassword(), request.passwordConfirmation());
        String passwordText = text(request.newPassword());
        if (passwordEncoder.matches(passwordText, identity.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "PASSWORD_REUSED",
                    "Choose a password different from the current password.");
        }

        identity.changePasswordHash(passwordEncoder.encode(passwordText), now);
        identities.save(identity);
        grantRoleIfMissing(account.getId(), UserRole.USER);
        grantRoleIfMissing(account.getId(), UserRole.ADMIN);
        grantRoleIfMissing(account.getId(), UserRole.OWNER);
        sessions.findByUserIdAndRevokedAtIsNull(account.getId())
                .forEach(session -> {
                    session.revoke(now);
                    sessions.save(session);
                });
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                account.getId(), "OWNER_RECOVERY_CLAIMED", account.getId(),
                "SUCCESS",
                "One-time OWNER claim completed with an explicit password reset."));
        return true;
    }

    private String configuredEmail() {
        try {
            return NsuEmailPolicy.require(properties.email());
        } catch (ApiException invalidConfiguration) {
            return "";
        }
    }

    private boolean claimTokenMatches(char[] candidate) {
        String rawToken = text(candidate);
        if (rawToken.length() < 43) {
            return false;
        }
        return tokenHasher.matches(rawToken,
                tokenHasher.hash(properties.claimToken()));
    }

    private boolean ownerExists() {
        return roles.findAll().stream().anyMatch(assignment ->
                UserRole.OWNER.name().equals(assignment.getRoleName()));
    }

    private void requireMatchingStrongPasswords(
            char[] password, char[] confirmation) {
        if (!Arrays.equals(password, confirmation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "Password confirmation does not match.");
        }
        passwordPolicy.requireStrong(password);
    }

    private void grantRoleIfMissing(UUID userId, UserRole role) {
        if (!roles.existsByUserIdAndRoleName(userId, role.name())) {
            roles.save(new UserRoleAssignment(userId, role));
        }
    }

    private static String text(char[] value) {
        return value == null ? "" : CharBuffer.wrap(value).toString();
    }
}
