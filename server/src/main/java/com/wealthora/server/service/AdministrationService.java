package com.wealthora.server.service;

import com.wealthora.server.api.AdminActionRequest;
import com.wealthora.server.api.AdminAuditResponse;
import com.wealthora.server.api.AdminOverviewResponse;
import com.wealthora.server.api.AdminSecurityResponse;
import com.wealthora.server.api.AdminSettingsRequest;
import com.wealthora.server.api.AdminSettingsResponse;
import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.DatabaseHealthResponse;
import com.wealthora.server.api.UserResponse;
import com.wealthora.server.config.PasswordRecoveryProperties;
import com.wealthora.server.config.RegistrationProperties;
import com.wealthora.server.config.SessionProperties;
import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuditLogEntry;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.SessionRecord;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRole;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.SessionPrincipal;
import java.nio.CharBuffer;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministrationService {

    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final UserRoleRepository roles;
    private final SessionRecordRepository sessions;
    private final AuditLogRepository auditLogs;
    private final ApplicationSettingsService settings;
    private final PasswordEncoder passwordEncoder;
    private final SessionProperties sessionProperties;
    private final RegistrationProperties registrationProperties;
    private final PasswordRecoveryProperties recoveryProperties;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Clock clock;

    public AdministrationService(
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            UserRoleRepository roles,
            SessionRecordRepository sessions,
            AuditLogRepository auditLogs,
            ApplicationSettingsService settings,
            PasswordEncoder passwordEncoder,
            SessionProperties sessionProperties,
            RegistrationProperties registrationProperties,
            PasswordRecoveryProperties recoveryProperties,
            JdbcTemplate jdbc,
            DataSource dataSource,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.sessions = sessions;
        this.auditLogs = auditLogs;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
        this.sessionProperties = sessionProperties;
        this.registrationProperties = registrationProperties;
        this.recoveryProperties = recoveryProperties;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview(SessionPrincipal principal) {
        requireAdministrator(principal);
        List<UserAccount> accounts = users.findAll();
        int owners = 0;
        int administrators = 0;
        int standardUsers = 0;
        for (UserAccount account : accounts) {
            Set<String> assigned = assignedRoles(account.getId());
            if (assigned.contains(UserRole.OWNER.name())) owners++;
            else if (assigned.contains(UserRole.ADMIN.name())) administrators++;
            else standardUsers++;
        }
        return new AdminOverviewResponse(accounts.size(),
                count(accounts, AccountStatus.ACTIVE),
                count(accounts, AccountStatus.PENDING_APPROVAL),
                count(accounts, AccountStatus.PENDING_EMAIL_VERIFICATION),
                count(accounts, AccountStatus.SUSPENDED),
                count(accounts, AccountStatus.DISABLED), owners,
                administrators, standardUsers,
                accounts.stream().mapToInt(
                        UserAccount::getFailedLoginAttempts).sum(),
                auditLogs.count());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(SessionPrincipal principal) {
        requireAdministrator(principal);
        return users.findAllByOrderByCreatedAtAsc().stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> pendingRegistrations(SessionPrincipal principal) {
        requireAdministrator(principal);
        return users.findByAccountStatusOrderByCreatedAtAsc(
                        AccountStatus.PENDING_APPROVAL).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> pendingVerifications(SessionPrincipal principal) {
        requireAdministrator(principal);
        return users.findByAccountStatusOrderByCreatedAtAsc(
                        AccountStatus.PENDING_EMAIL_VERIFICATION).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAuditResponse> auditEvents(SessionPrincipal principal) {
        requireAdministrator(principal);
        return auditLogs.findTop200ByOrderByOccurredAtDesc().stream()
                .map(entry -> new AdminAuditResponse(entry.getOccurredAt(),
                        text(entry.getActorUserId()), entry.getAction(),
                        text(entry.getTargetUserId()), entry.getOutcome(),
                        entry.getReason() == null ? "" : entry.getReason()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSecurityResponse security(SessionPrincipal principal) {
        requireAdministrator(principal);
        return new AdminSecurityResponse(
                "At least 12 characters with uppercase, lowercase, number, and symbol",
                sessionProperties.accessExpiry().toString(),
                sessionProperties.refreshExpiry().toString(),
                sessionProperties.lockDuration().toString(),
                sessionProperties.maximumFailedAttempts(),
                registrationProperties.verificationExpiry().toString(),
                registrationProperties.maximumVerificationAttempts(),
                recoveryProperties.resetExpiry().toString());
    }

    @Transactional(readOnly = true)
    public AdminSettingsResponse applicationSettings(
            SessionPrincipal principal) {
        requireAdministrator(principal);
        return new AdminSettingsResponse(settings.requiresAdminApproval());
    }

    @Transactional
    public AdminSettingsResponse updateSettings(
            SessionPrincipal principal, AdminSettingsRequest request) {
        requireOwner(principal);
        requirePassword(principal, request.currentPassword());
        Instant now = clock.instant();
        settings.setRequiresAdminApproval(
                request.registrationRequiresAdminApproval(),
                principal.userId(), now);
        audit(principal, "APPLICATION_SETTINGS_CHANGED", principal.userId(),
                requiredReason(request.reason()), now);
        return new AdminSettingsResponse(
                request.registrationRequiresAdminApproval());
    }

    @Transactional(readOnly = true)
    public DatabaseHealthResponse databaseHealth(SessionPrincipal principal) {
        requireAdministrator(principal);
        try (Connection connection = dataSource.getConnection()) {
            jdbc.queryForObject("select 1", Integer.class);
            Long migrations = jdbc.queryForObject(
                    "select count(*) from schema_migrations", Long.class);
            return new DatabaseHealthResponse("UP",
                    connection.getMetaData().getDatabaseProductName(),
                    migrations == null ? 0 : migrations,
                    users.count(), sessions.countByRevokedAtIsNull());
        } catch (Exception exception) {
            return new DatabaseHealthResponse("DOWN", "Unavailable", 0,
                    0, 0);
        }
    }

    @Transactional
    public UserResponse approve(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        UserAccount target = manageableTarget(principal, targetId);
        if (!target.isEmailVerified()
                || target.getAccountStatus() != AccountStatus.PENDING_APPROVAL) {
            throw invalidStatus("Only a verified pending registration can be approved.");
        }
        return changeStatus(principal, target, AccountStatus.ACTIVE,
                "REGISTRATION_APPROVED", request.reason());
    }

    @Transactional
    public UserResponse reject(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        UserAccount target = manageableTarget(principal, targetId);
        if (target.getAccountStatus() != AccountStatus.PENDING_APPROVAL
                && target.getAccountStatus()
                        != AccountStatus.PENDING_EMAIL_VERIFICATION) {
            throw invalidStatus("Only a pending registration can be rejected.");
        }
        return changeStatus(principal, target, AccountStatus.DISABLED,
                "REGISTRATION_REJECTED", request.reason());
    }

    @Transactional
    public UserResponse activate(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        UserAccount target = manageableTarget(principal, targetId);
        if (!target.isEmailVerified()
                || (target.getAccountStatus() != AccountStatus.SUSPENDED
                && target.getAccountStatus() != AccountStatus.DISABLED)) {
            throw invalidStatus("Only a verified suspended or disabled user can be activated.");
        }
        return changeStatus(principal, target, AccountStatus.ACTIVE,
                "USER_ACTIVATED", request.reason());
    }

    @Transactional
    public UserResponse suspend(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        UserAccount target = manageableTarget(principal, targetId);
        if (target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw invalidStatus("Only an active user can be suspended.");
        }
        return changeStatus(principal, target, AccountStatus.SUSPENDED,
                "USER_SUSPENDED", request.reason());
    }

    @Transactional
    public UserResponse disable(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        UserAccount target = manageableTarget(principal, targetId);
        if (target.getAccountStatus() == AccountStatus.DISABLED) {
            throw invalidStatus("This user is already disabled.");
        }
        return changeStatus(principal, target, AccountStatus.DISABLED,
                "USER_DISABLED", request.reason());
    }

    @Transactional
    public UserResponse grantAdministrator(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        requireOwner(principal);
        UserAccount target = target(targetId);
        requireNotOwner(targetId);
        if (!target.isEmailVerified()
                || target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw invalidStatus("Only an active verified user can become an administrator.");
        }
        requirePassword(principal, request.currentPassword());
        if (roles.existsByUserIdAndRoleName(
                targetId, UserRole.ADMIN.name())) {
            throw invalidStatus("The selected user is already an administrator.");
        }
        roles.save(new UserRoleAssignment(targetId, UserRole.ADMIN));
        Instant now = clock.instant();
        audit(principal, "ADMIN_GRANTED", targetId,
                requiredReason(request.reason()), now);
        return response(target);
    }

    @Transactional
    public UserResponse revokeAdministrator(
            SessionPrincipal principal, UUID targetId,
            AdminActionRequest request) {
        requireOwner(principal);
        UserAccount target = target(targetId);
        requireNotOwner(targetId);
        requirePassword(principal, request.currentPassword());
        if (!roles.existsByUserIdAndRoleName(
                targetId, UserRole.ADMIN.name())) {
            throw invalidStatus("The selected user is not an administrator.");
        }
        roles.deleteByUserIdAndRoleName(targetId, UserRole.ADMIN.name());
        Instant now = clock.instant();
        audit(principal, "ADMIN_REVOKED", targetId,
                requiredReason(request.reason()), now);
        return response(target);
    }

    private UserResponse changeStatus(
            SessionPrincipal principal, UserAccount target,
            AccountStatus status, String action, String reason) {
        String requiredReason = requiredReason(reason);
        Instant now = clock.instant();
        target.changeStatus(status, now);
        users.save(target);
        if (status != AccountStatus.ACTIVE) {
            sessions.findByUserIdAndRevokedAtIsNull(target.getId())
                    .forEach(session -> {
                        session.revoke(now);
                        sessions.save(session);
                    });
        }
        audit(principal, action, target.getId(), requiredReason, now);
        return response(target);
    }

    private UserAccount manageableTarget(
            SessionPrincipal principal, UUID targetId) {
        requireAdministrator(principal);
        if (principal.userId().equals(targetId)) {
            throw denied("You cannot change your own account status.");
        }
        UserAccount target = target(targetId);
        Set<String> targetRoles = assignedRoles(targetId);
        if (targetRoles.contains(UserRole.OWNER.name())) {
            throw denied("The OWNER account cannot be modified.");
        }
        if (targetRoles.contains(UserRole.ADMIN.name())
                && !isOwner(principal)) {
            throw denied("Only the OWNER can modify an administrator.");
        }
        return target;
    }

    private void requireNotOwner(UUID targetId) {
        if (assignedRoles(targetId).contains(UserRole.OWNER.name())) {
            throw denied("The OWNER role cannot be removed or replaced.");
        }
    }

    private void requirePassword(
            SessionPrincipal principal, char[] currentPassword) {
        if (currentPassword == null || currentPassword.length == 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "REAUTHENTICATION_REQUIRED",
                    "Current OWNER password confirmation is required.");
        }
        try {
            AuthenticationIdentity password = identities
                    .findByUserIdAndProvider(principal.userId(),
                            AuthProvider.PASSWORD)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                            "REAUTHENTICATION_REQUIRED",
                            "Current OWNER password confirmation is unavailable."));
            String text = CharBuffer.wrap(currentPassword).toString();
            if (!passwordEncoder.matches(text, password.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                        "REAUTHENTICATION_FAILED",
                        "Current OWNER password confirmation failed.");
            }
        } finally {
            Arrays.fill(currentPassword, '\0');
        }
    }

    private UserAccount target(UUID identifier) {
        return users.findById(identifier).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "The selected user no longer exists."));
    }

    private UserResponse response(UserAccount user) {
        return UserResponse.from(user, assignedRoles(user.getId()),
                identities.findByUserId(user.getId()));
    }

    private Set<String> assignedRoles(UUID userId) {
        return roles.findByUserId(userId).stream()
                .map(UserRoleAssignment::getRoleName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void requireAdministrator(SessionPrincipal principal) {
        if (principal == null || (!principal.roles().contains(
                UserRole.ADMIN.name()) && !isOwner(principal))) {
            throw denied("Administrator permission is required.");
        }
    }

    private void requireOwner(SessionPrincipal principal) {
        if (principal == null || !isOwner(principal)) {
            throw denied("OWNER permission is required.");
        }
    }

    private static boolean isOwner(SessionPrincipal principal) {
        return principal != null
                && principal.roles().contains(UserRole.OWNER.name());
    }

    private void audit(
            SessionPrincipal principal, String action,
            UUID target, String reason, Instant now) {
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                principal.userId(), action, target, "SUCCESS", reason));
    }

    private static int count(
            List<UserAccount> users, AccountStatus status) {
        return (int) users.stream().filter(
                user -> user.getAccountStatus() == status).count();
    }

    private static String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                    "A reason is required for this change.");
        }
        String clean = reason.strip();
        if (clean.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_TOO_LONG",
                    "The reason must not exceed 500 characters.");
        }
        return clean;
    }

    private static String text(UUID value) {
        return value == null ? "" : value.toString();
    }

    private static ApiException denied(String message) {
        return new ApiException(HttpStatus.FORBIDDEN,
                "ADMIN_ACTION_FORBIDDEN", message);
    }

    private static ApiException invalidStatus(String message) {
        return new ApiException(HttpStatus.CONFLICT,
                "ACCOUNT_STATUS_INVALID", message);
    }
}
