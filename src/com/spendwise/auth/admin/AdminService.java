package com.spendwise.auth.admin;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.AuthorizationService;
import com.spendwise.auth.UserRole;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.audit.AuditAction;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.auth.audit.AuditRepository;
import com.spendwise.auth.local.LocalDesktopAuthService;
import com.spendwise.auth.local.LocalUserRecord;
import com.spendwise.auth.local.LocalUserRepository;
import com.spendwise.config.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AdminService {

    private final LocalUserRepository userRepository;
    private final AuditRepository auditRepository;
    private final LocalDesktopAuthService authService;
    private final AuthorizationService authorizationService;
    private final Clock clock;
    private final Path backupDirectory;

    public AdminService(
            LocalUserRepository userRepository,
            AuditRepository auditRepository,
            LocalDesktopAuthService authService) {
        this(userRepository, auditRepository, authService,
                new AuthorizationService(), Clock.systemUTC(),
                AppPaths.getBackupDirectory());
    }

    AdminService(
            LocalUserRepository userRepository,
            AuditRepository auditRepository,
            LocalDesktopAuthService authService,
            AuthorizationService authorizationService,
            Clock clock,
            Path backupDirectory) {
        this.userRepository = Objects.requireNonNull(
                userRepository, "User repository is required.");
        this.auditRepository = Objects.requireNonNull(
                auditRepository, "Audit repository is required.");
        this.authService = Objects.requireNonNull(
                authService, "Authentication service is required.");
        this.authorizationService = Objects.requireNonNull(
                authorizationService, "Authorization service is required.");
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.backupDirectory = Objects.requireNonNull(
                backupDirectory, "Backup directory is required.")
                .toAbsolutePath().normalize();
    }

    public List<AuthenticatedUser> listUsers(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return userRepository.findAll().stream().map(LocalUserRecord::user)
                .sorted(Comparator.comparing(AuthenticatedUser::getCreatedAt))
                .toList();
    }

    public AdminOverview getOverview(UserSession actor) {
        authorizationService.requireAdmin(actor);
        List<LocalUserRecord> records = userRepository.findAll();
        List<AuthenticatedUser> users = records.stream()
                .map(LocalUserRecord::user).toList();
        int active = (int) users.stream().filter(user ->
                user.getAccountStatus() == AccountStatus.ACTIVE).count();
        int suspended = (int) users.stream().filter(user ->
                user.getAccountStatus() == AccountStatus.SUSPENDED).count();
        int owners = (int) users.stream().filter(user ->
                user.hasRole(UserRole.OWNER)).count();
        int administrators = (int) users.stream().filter(user ->
                user.hasRole(UserRole.ADMIN)
                        && !user.hasRole(UserRole.OWNER)).count();
        int standardUsers = (int) users.stream().filter(user ->
                !user.hasRole(UserRole.ADMIN)
                        && !user.hasRole(UserRole.OWNER)).count();
        int failures = records.stream().mapToInt(
                LocalUserRecord::failedLoginAttempts).sum();
        return new AdminOverview(
                users.size(), active,
                (int) users.stream().filter(user -> user.getAccountStatus()
                        == AccountStatus.PENDING_APPROVAL).count(),
                (int) users.stream().filter(user -> user.getAccountStatus()
                        == AccountStatus.PENDING_EMAIL_VERIFICATION).count(),
                suspended,
                (int) users.stream().filter(user -> user.getAccountStatus()
                        == AccountStatus.DISABLED).count(),
                owners, administrators,
                standardUsers, failures, lastBackup(), "Healthy");
    }

    public List<AuditEvent> getAuditEvents(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return auditRepository.findAll();
    }

    public List<AuthenticatedUser> listPendingRegistrations(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return listUsers(actor).stream().filter(user ->
                user.getAccountStatus() == AccountStatus.PENDING_APPROVAL)
                .toList();
    }

    public List<AuthenticatedUser> listPendingVerifications(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return listUsers(actor).stream().filter(user -> user.getAccountStatus()
                == AccountStatus.PENDING_EMAIL_VERIFICATION).toList();
    }

    public AdminSecurityStatus getSecurityStatus(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return new AdminSecurityStatus(
                "6-128 characters with an English letter and number; no outer spaces",
                "Local session", "Local session", "15 minutes", 5,
                "Local repository", 5, "Local repository");
    }

    public AdminApplicationSettings getApplicationSettings(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return new AdminApplicationSettings(false, false);
    }

    public DatabaseHealthStatus getDatabaseHealth(UserSession actor) {
        authorizationService.requireAdmin(actor);
        return new DatabaseHealthStatus("UP", "Validated local CSV storage",
                0, userRepository.findAll().size(), 1);
    }

    public AuthenticatedUser approveRegistration(
            UserSession actor, String targetUserIdentifier, String reason) {
        authorizationService.requireAdmin(actor);
        LocalUserRecord target = requiredUser(targetUserIdentifier);
        if (!target.user().isEmailVerified()
                || target.user().getAccountStatus()
                        != AccountStatus.PENDING_APPROVAL) {
            throw new AuthException(
                    "Only a verified pending registration can be approved.");
        }
        return changeStatus(actor, targetUserIdentifier, AccountStatus.ACTIVE,
                AuditAction.REGISTRATION_APPROVED, reason);
    }

    public AuthenticatedUser rejectRegistration(
            UserSession actor, String targetUserIdentifier, String reason) {
        authorizationService.requireAdmin(actor);
        LocalUserRecord target = requiredUser(targetUserIdentifier);
        if (target.user().getAccountStatus() != AccountStatus.PENDING_APPROVAL
                && target.user().getAccountStatus()
                        != AccountStatus.PENDING_EMAIL_VERIFICATION) {
            throw new AuthException(
                    "Only a pending registration can be rejected.");
        }
        return changeStatus(actor, targetUserIdentifier, AccountStatus.DISABLED,
                AuditAction.REGISTRATION_REJECTED, reason);
    }

    public AuthenticatedUser disableUser(
            UserSession actor, String targetUserIdentifier, String reason) {
        authorizationService.requireAdmin(actor);
        return changeStatus(actor, targetUserIdentifier, AccountStatus.DISABLED,
                AuditAction.USER_DISABLED, reason);
    }

    public AdminApplicationSettings updateApplicationSettings(
            UserSession actor, boolean approvalRequired,
            char[] ownerPassword, String reason) {
        if (!actor.isOwner()) {
            throw new AuthException("Only the OWNER can change application settings.");
        }
        throw new AuthException(
                "Registration approval is fixed off for verified local accounts.");
    }

    public AuthenticatedUser suspendUser(
            UserSession actor, String targetUserIdentifier, String reason) {
        authorizationService.requireAdmin(actor);
        return changeStatus(actor, targetUserIdentifier,
                AccountStatus.SUSPENDED, AuditAction.USER_SUSPENDED, reason);
    }

    public AuthenticatedUser activateUser(
            UserSession actor, String targetUserIdentifier, String reason) {
        authorizationService.requireAdmin(actor);
        return changeStatus(actor, targetUserIdentifier,
                AccountStatus.ACTIVE, AuditAction.USER_ACTIVATED, reason);
    }

    public void resetUserPassword(
            UserSession actor,
            String targetUserIdentifier,
            char[] administratorPassword,
            char[] newPassword,
            char[] passwordConfirmation,
            String reason) {
        authorizationService.requireAdmin(actor);
        String requiredReason = requireReason(reason);
        LocalUserRecord target = requiredUser(targetUserIdentifier);
        authService.resetPasswordByAdministrator(actor,
                targetUserIdentifier, administratorPassword,
                newPassword, passwordConfirmation);
        audit(actor, AuditAction.PASSWORD_RESET_COMPLETED,
                target.user(), requiredReason);
    }

    public AuthenticatedUser grantAdministrator(
            UserSession actor,
            String targetUserIdentifier,
            char[] ownerPassword,
            String reason) {
        authorizationService.requireCanManageAdministrators(
                actor, requiredUserForAuthorization(targetUserIdentifier));
        return changeAdministrator(actor, targetUserIdentifier, true,
                ownerPassword, reason);
    }

    public AuthenticatedUser revokeAdministrator(
            UserSession actor,
            String targetUserIdentifier,
            char[] ownerPassword,
            String reason) {
        authorizationService.requireCanManageAdministrators(
                actor, requiredUserForAuthorization(targetUserIdentifier));
        return changeAdministrator(actor, targetUserIdentifier, false,
                ownerPassword, reason);
    }

    private AuthenticatedUser requiredUserForAuthorization(String identifier) {
        return requiredUser(identifier).user();
    }

    private AuthenticatedUser changeStatus(
            UserSession actor,
            String targetUserIdentifier,
            AccountStatus status,
            AuditAction action,
            String reason) {
        authorizationService.requireAdmin(actor);
        String requiredReason = requireReason(reason);
        LocalUserRecord target = requiredUser(targetUserIdentifier);
        if (target.user().hasRole(UserRole.OWNER)) {
            throw new AuthException("The primary OWNER cannot be suspended.");
        }
        if (actor.getUserIdentifier().equals(targetUserIdentifier)) {
            throw new AuthException("You cannot change your own account status.");
        }
        if (target.user().hasRole(UserRole.ADMIN) && !actor.isOwner()) {
            throw new AuthException(
                    "Only the OWNER can change an administrator account.");
        }
        Instant now = clock.instant();
        AuthenticatedUser updated = target.user().withStatus(status, now);
        userRepository.save(target.withAuthenticationState(updated,
                status == AccountStatus.ACTIVE
                        ? 0 : target.failedLoginAttempts(),
                status == AccountStatus.ACTIVE ? null : target.lockedUntil()));
        audit(actor, action, updated, requiredReason);
        return updated;
    }

    private AuthenticatedUser changeAdministrator(
            UserSession actor,
            String targetUserIdentifier,
            boolean grant,
            char[] ownerPassword,
            String reason) {
        LocalUserRecord target = requiredUser(targetUserIdentifier);
        authorizationService.requireCanManageAdministrators(
                actor, target.user());
        String requiredReason = requireReason(reason);
        EnumSet<UserRole> roles = EnumSet.copyOf(target.user().getRoles());
        if (grant && roles.contains(UserRole.ADMIN)) {
            throw new AuthException(
                    "The selected user is already an administrator.");
        }
        if (!grant && !roles.contains(UserRole.ADMIN)) {
            throw new AuthException(
                    "The selected user is not an administrator.");
        }
        if (!authService.verifyCurrentPassword(ownerPassword)) {
            throw new AuthException("OWNER password confirmation failed.");
        }
        if (grant) roles.add(UserRole.ADMIN); else roles.remove(UserRole.ADMIN);
        roles.add(UserRole.USER);
        Instant now = clock.instant();
        AuthenticatedUser updated = target.user().withRoles(
                Set.copyOf(roles), now);
        userRepository.save(target.withAuthenticationState(updated,
                target.failedLoginAttempts(), target.lockedUntil()));
        audit(actor, grant ? AuditAction.ADMIN_GRANTED
                : AuditAction.ADMIN_REVOKED, updated, requiredReason);
        return updated;
    }

    private LocalUserRecord requiredUser(String identifier) {
        String required = Objects.requireNonNull(
                identifier, "Target user ID is required.").strip();
        return userRepository.findById(required).orElseThrow(
                () -> new AuthException("The selected user no longer exists."));
    }

    private void audit(
            UserSession actor,
            AuditAction action,
            AuthenticatedUser target,
            String reason) {
        auditRepository.append(new AuditEvent(clock.instant(),
                actor.getUserIdentifier(), action,
                target.getUserIdentifier(), "SUCCESS", reason));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AuthException("A reason is required for this change.");
        }
        return reason.strip();
    }

    private String lastBackup() {
        if (!Files.isDirectory(backupDirectory)) return "None";
        try (var files = Files.list(backupDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".zip"))
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        } catch (IOException exception) {
                            return Instant.EPOCH;
                        }
                    }))
                    .map(path -> path.getFileName().toString())
                    .orElse("None");
        } catch (IOException | SecurityException exception) {
            return "Unavailable";
        }
    }
}
