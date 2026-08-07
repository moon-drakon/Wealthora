package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.ChangePasswordRequest;
import com.wealthora.server.api.ResetPasswordRequest;
import com.wealthora.server.api.SetPasswordRequest;
import com.wealthora.server.config.PasswordRecoveryProperties;
import com.wealthora.server.domain.AuditLogEntry;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.PasswordResetToken;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.mail.PasswordResetMailDelivery;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.PasswordResetTokenRepository;
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.security.NsuEmailPolicy;
import com.wealthora.server.security.PasswordPolicy;
import com.wealthora.server.security.SessionPrincipal;
import com.wealthora.server.security.TokenHasher;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {

    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionRecordRepository sessions;
    private final AuditLogRepository auditLogs;
    private final PasswordResetMailDelivery mailDelivery;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenHasher tokenHasher;
    private final PasswordRecoveryProperties properties;
    private final OwnerClaimService ownerClaimService;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public PasswordRecoveryService(
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            PasswordResetTokenRepository resetTokens,
            SessionRecordRepository sessions,
            AuditLogRepository auditLogs,
            PasswordResetMailDelivery mailDelivery,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            TokenHasher tokenHasher,
            PasswordRecoveryProperties properties,
            OwnerClaimService ownerClaimService,
            SecureRandom secureRandom,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.auditLogs = auditLogs;
        this.mailDelivery = mailDelivery;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.ownerClaimService = ownerClaimService;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String email;
        try {
            email = NsuEmailPolicy.require(rawEmail);
        } catch (ApiException exception) {
            return;
        }
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || !user.isEmailVerified()
                || identities.findByUserIdAndProvider(
                        user.getId(), AuthProvider.PASSWORD).isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        boolean coolingDown = resetTokens
                .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .filter(token -> token.getCreatedAt()
                        .plus(properties.requestCooldown()).isAfter(now))
                .isPresent();
        if (coolingDown) return;

        String rawToken = opaqueToken();
        try {
            mailDelivery.sendPasswordResetToken(email, rawToken);
        } catch (RuntimeException deliveryFailure) {
            audit(user, "PASSWORD_RESET_DELIVERY_FAILED", "FAILED",
                    "Password-reset delivery unavailable.", now);
            return;
        }
        resetTokens.findByUserIdAndConsumedAtIsNull(user.getId())
                .forEach(previous -> {
                    previous.consume(now);
                    resetTokens.save(previous);
                });
        resetTokens.save(new PasswordResetToken(UUID.randomUUID(),
                user.getId(), tokenHasher.hash(rawToken), now,
                now.plus(properties.resetExpiry())));
        audit(user, "PASSWORD_RESET_REQUESTED", "SUCCESS",
                "One-time reset token delivered.", now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = clock.instant();
        try {
            String email = NsuEmailPolicy.require(request.email());
            String rawToken = text(request.resetToken());
            if (ownerClaimService.tryClaim(email, request, now)) {
                return;
            }
            UserAccount user = users.findByEmail(email).orElse(null);
            PasswordResetToken token = user == null ? null : resetTokens
                    .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                    .orElse(null);
            AuthenticationIdentity identity = user == null ? null
                    : identities.findByUserIdAndProvider(
                            user.getId(), AuthProvider.PASSWORD).orElse(null);
            if (token == null || user == null || identity == null
                    || !token.isUsableAt(now,
                            properties.maximumResetAttempts())
                    || rawToken.length() < 32
                    || !tokenHasher.matches(rawToken, token.getTokenHash())) {
                recordFailedAttempt(token, now);
                throw invalidReset();
            }
            requireMatchingStrongPasswords(
                    request.newPassword(), request.passwordConfirmation());
            if (passwordEncoder.matches(text(request.newPassword()),
                    identity.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "PASSWORD_REUSED",
                        "Choose a password different from the current password.");
            }
            identity.changePasswordHash(encode(request.newPassword()), now);
            identities.save(identity);
            resetTokens.findByUserIdAndConsumedAtIsNull(user.getId())
                    .forEach(outstanding -> {
                        outstanding.consume(now);
                        resetTokens.save(outstanding);
                    });
            revokeAllSessions(user.getId(), now);
            audit(user, "PASSWORD_RESET_COMPLETED", "SUCCESS",
                    "Password changed and all sessions revoked.", now);
        } finally {
            clear(request.resetToken());
            clear(request.newPassword());
            clear(request.passwordConfirmation());
        }
    }

    @Transactional
    public void changePassword(
            SessionPrincipal principal, ChangePasswordRequest request) {
        Instant now = clock.instant();
        try {
            UserAccount user = users.findById(principal.userId())
                    .orElseThrow(PasswordRecoveryService::invalidSession);
            AuthenticationIdentity identity = identities
                    .findByUserIdAndProvider(
                            user.getId(), AuthProvider.PASSWORD)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "PASSWORD_NOT_AVAILABLE",
                            "This account does not use password authentication."));
            if (!passwordEncoder.matches(text(request.currentPassword()),
                    identity.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                        "CURRENT_PASSWORD_INCORRECT",
                        "The current password is incorrect.");
            }
            requireMatchingStrongPasswords(
                    request.newPassword(), request.passwordConfirmation());
            if (passwordEncoder.matches(text(request.newPassword()),
                    identity.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "PASSWORD_REUSED",
                        "Choose a password different from the current password.");
            }
            identity.changePasswordHash(encode(request.newPassword()), now);
            identities.save(identity);
            revokeAllSessions(user.getId(), now);
            audit(user, "PASSWORD_CHANGED", "SUCCESS",
                    "Password changed and all sessions revoked.", now);
        } finally {
            clear(request.currentPassword());
            clear(request.newPassword());
            clear(request.passwordConfirmation());
        }
    }

    @Transactional
    public void setPassword(
            SessionPrincipal principal, SetPasswordRequest request) {
        Instant now = clock.instant();
        try {
            UserAccount user = users.findById(principal.userId())
                    .orElseThrow(PasswordRecoveryService::invalidSession);
            if (identities.findByUserIdAndProvider(
                    user.getId(), AuthProvider.PASSWORD).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "PASSWORD_ALREADY_SET",
                        "This account already has a password. Change it instead.");
            }
            requireMatchingStrongPasswords(
                    request.newPassword(), request.passwordConfirmation());
            identities.save(new AuthenticationIdentity(
                    UUID.randomUUID(), user.getId(), AuthProvider.PASSWORD,
                    encode(request.newPassword()), now));
            revokeAllSessions(user.getId(), now);
            audit(user, "PASSWORD_SET", "SUCCESS",
                    "Password added and all sessions revoked.", now);
        } finally {
            clear(request.newPassword());
            clear(request.passwordConfirmation());
        }
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

    private void revokeAllSessions(UUID userId, Instant now) {
        sessions.findByUserIdAndRevokedAtIsNull(userId).forEach(session -> {
            session.revoke(now);
            sessions.save(session);
        });
    }

    private void recordFailedAttempt(PasswordResetToken token, Instant now) {
        if (token == null || !token.isUsableAt(now,
                properties.maximumResetAttempts())) return;
        token.recordFailure();
        if (token.getFailedAttempts() >= properties.maximumResetAttempts()) {
            token.consume(now);
        }
        resetTokens.save(token);
    }

    private String encode(char[] password) {
        return passwordEncoder.encode(text(password));
    }

    private String opaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void audit(
            UserAccount user, String action, String outcome,
            String reason, Instant now) {
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                user.getId(), action, user.getId(), outcome, reason));
    }

    private static String text(char[] value) {
        return value == null ? "" : CharBuffer.wrap(value).toString();
    }

    private static void clear(char[] value) {
        if (value != null) Arrays.fill(value, '\0');
    }

    private static ApiException invalidReset() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "PASSWORD_RESET_FAILED",
                "The password-reset token is invalid or expired.");
    }

    private static ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID", "The session is no longer active.");
    }
}
