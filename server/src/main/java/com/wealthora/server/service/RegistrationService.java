package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.RegisterRequest;
import com.wealthora.server.api.UserResponse;
import com.wealthora.server.config.RegistrationProperties;
import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuditLogEntry;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.EmailVerification;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRole;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.mail.VerificationMailDelivery;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.EmailVerificationRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.NsuEmailPolicy;
import com.wealthora.server.security.PasswordPolicy;
import com.wealthora.server.security.TokenHasher;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final UserRoleRepository roles;
    private final EmailVerificationRepository verifications;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenHasher tokenHasher;
    private final VerificationMailDelivery mailDelivery;
    private final RegistrationProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RegistrationService(
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            UserRoleRepository roles,
            EmailVerificationRepository verifications,
            AuditLogRepository auditLogs,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            TokenHasher tokenHasher,
            VerificationMailDelivery mailDelivery,
            RegistrationProperties properties,
            SecureRandom secureRandom,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.verifications = verifications;
        this.auditLogs = auditLogs;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenHasher = tokenHasher;
        this.mailDelivery = mailDelivery;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!request.termsAccepted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TERMS_REQUIRED",
                    "Accept the terms and privacy notice to create an account.");
        }
        String email = NsuEmailPolicy.require(request.email());
        if (!Arrays.equals(request.password(), request.passwordConfirmation())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "Password confirmation does not match.");
        }
        passwordPolicy.requireStrong(request.password());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "This email is already registered.");
        }
        String fullName = required(request.fullName(), "Full name", 160);
        String studentId = optional(request.studentId(), 80);
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        UserAccount user = users.save(new UserAccount(
                userId, fullName, email, studentId,
                AccountStatus.PENDING_EMAIL_VERIFICATION, now));
        String passwordText = CharBuffer.wrap(request.password()).toString();
        try {
            identities.save(new AuthenticationIdentity(UUID.randomUUID(),
                    userId, AuthProvider.PASSWORD,
                    passwordEncoder.encode(passwordText), now));
        } finally {
            passwordText = null;
            Arrays.fill(request.password(), '\0');
            Arrays.fill(request.passwordConfirmation(), '\0');
        }
        roles.save(new UserRoleAssignment(userId, UserRole.USER));
        issueVerification(user, now, false);
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now, userId,
                "REGISTRATION_CREATED", userId, "SUCCESS",
                "NSU email verification required."));
        return UserResponse.from(user, Set.of(UserRole.USER.name()));
    }

    @Transactional
    public UserResponse verify(String rawEmail, String code) {
        String email = NsuEmailPolicy.require(rawEmail);
        UserAccount user = users.findByEmail(email).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST,
                        "VERIFICATION_FAILED",
                        "The verification code is invalid or expired."));
        EmailVerification verification = verifications
                .findFirstByUserIdOrderBySentAtDesc(user.getId())
                .orElseThrow(() -> verificationFailed());
        Instant now = clock.instant();
        if (verification.getConsumedAt() != null
                || !verification.getExpiresAt().isAfter(now)
                || verification.getFailedAttempts()
                        >= properties.maximumVerificationAttempts()) {
            throw verificationFailed();
        }
        if (!tokenHasher.matches(code, verification.getTokenHash())) {
            verification.recordFailure();
            verifications.save(verification);
            throw verificationFailed();
        }
        verification.consume(now);
        AccountStatus next = properties.requiresAdminApproval()
                ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;
        user.verifyEmail(next, now);
        verifications.save(verification);
        users.save(user);
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                user.getId(), "EMAIL_VERIFIED", user.getId(), "SUCCESS",
                next == AccountStatus.PENDING_APPROVAL
                        ? "Awaiting administrator approval." : "Activated."));
        return response(user);
    }

    @Transactional
    public void resend(String rawEmail) {
        String email = NsuEmailPolicy.require(rawEmail);
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || user.isEmailVerified()) return;
        Instant now = clock.instant();
        verifications.findFirstByUserIdOrderBySentAtDesc(user.getId())
                .ifPresent(previous -> {
                    if (previous.getSentAt().plus(properties.resendCooldown())
                            .isAfter(now)) {
                        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                                "RESEND_COOLDOWN",
                                "Wait before requesting another verification message.");
                    }
                });
        issueVerification(user, now, true);
    }

    private void issueVerification(
            UserAccount user, Instant now, boolean resend) {
        String code = String.format("%08d",
                secureRandom.nextInt(100_000_000));
        verifications.save(new EmailVerification(UUID.randomUUID(),
                user.getId(), tokenHasher.hash(code), now,
                now.plus(properties.verificationExpiry())));
        mailDelivery.sendVerificationCode(user.getEmail(), code);
        if (resend) {
            auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                    user.getId(), "VERIFICATION_RESENT", user.getId(),
                    "SUCCESS", "Verification message resent."));
        }
    }

    private UserResponse response(UserAccount user) {
        Set<String> assigned = roles.findByUserId(user.getId()).stream()
                .map(UserRoleAssignment::getRoleName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return UserResponse.from(user, assigned);
    }

    private static ApiException verificationFailed() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "VERIFICATION_FAILED",
                "The verification code is invalid or expired.");
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED", name + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED", name + " is too long.");
        }
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED", "Student ID is too long.");
        }
        return normalized;
    }
}
