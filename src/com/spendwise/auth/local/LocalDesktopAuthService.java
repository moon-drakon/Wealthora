package com.spendwise.auth.local;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthProvider;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.EmailAddressPolicy;
import com.spendwise.auth.NsuEmailPolicy;
import com.spendwise.auth.OwnerConfiguration;
import com.spendwise.auth.OwnerSetupService;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserRole;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.audit.AuditAction;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.auth.audit.AuditRepository;
import com.spendwise.auth.registration.RegistrationGateway;
import com.spendwise.auth.registration.UnconfiguredRegistrationGateway;
import com.spendwise.config.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class LocalDesktopAuthService
        implements AuthService, OwnerSetupService {

    public static final int MAXIMUM_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String GENERIC_SIGN_IN_FAILURE =
            "Email or password is incorrect, or the account is unavailable.";
    private static final char[] DUMMY_PASSWORD =
            "NotAReal1!Password".toCharArray();

    private final LocalUserRepository userRepository;
    private final PasswordService passwordService;
    private final OwnerConfiguration ownerConfiguration;
    private final SessionManager sessionManager;
    private final AuditRepository auditRepository;
    private final LegacyDataMigrationService migrationService;
    private final Clock clock;
    private final Function<String, java.nio.file.Path> workspaceResolver;
    private final String dummyPasswordHash;
    private final RegistrationGateway registrationGateway;

    public LocalDesktopAuthService(
            LocalUserRepository userRepository,
            PasswordService passwordService,
            OwnerConfiguration ownerConfiguration,
            SessionManager sessionManager,
            AuditRepository auditRepository,
            LegacyDataMigrationService migrationService) {
        this(userRepository, passwordService, ownerConfiguration,
                sessionManager, auditRepository, migrationService,
                new UnconfiguredRegistrationGateway());
    }

    public LocalDesktopAuthService(
            LocalUserRepository userRepository,
            PasswordService passwordService,
            OwnerConfiguration ownerConfiguration,
            SessionManager sessionManager,
            AuditRepository auditRepository,
            LegacyDataMigrationService migrationService,
            RegistrationGateway registrationGateway) {
        this(userRepository, passwordService, ownerConfiguration,
                sessionManager, auditRepository, migrationService,
                Clock.systemUTC(), AppPaths::getUserDataDirectory,
                registrationGateway);
    }

    LocalDesktopAuthService(
            LocalUserRepository userRepository,
            PasswordService passwordService,
            OwnerConfiguration ownerConfiguration,
            SessionManager sessionManager,
            AuditRepository auditRepository,
            LegacyDataMigrationService migrationService,
            Clock clock,
            Function<String, java.nio.file.Path> workspaceResolver) {
        this(userRepository, passwordService, ownerConfiguration,
                sessionManager, auditRepository, migrationService, clock,
                workspaceResolver, new UnconfiguredRegistrationGateway());
    }

    LocalDesktopAuthService(
            LocalUserRepository userRepository,
            PasswordService passwordService,
            OwnerConfiguration ownerConfiguration,
            SessionManager sessionManager,
            AuditRepository auditRepository,
            LegacyDataMigrationService migrationService,
            Clock clock,
            Function<String, java.nio.file.Path> workspaceResolver,
            RegistrationGateway registrationGateway) {
        this.userRepository = Objects.requireNonNull(
                userRepository, "User repository is required.");
        this.passwordService = Objects.requireNonNull(
                passwordService, "Password service is required.");
        this.ownerConfiguration = Objects.requireNonNull(
                ownerConfiguration, "Owner configuration is required.");
        this.sessionManager = Objects.requireNonNull(
                sessionManager, "Session manager is required.");
        this.auditRepository = Objects.requireNonNull(
                auditRepository, "Audit repository is required.");
        this.migrationService = Objects.requireNonNull(
                migrationService, "Migration service is required.");
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.workspaceResolver = Objects.requireNonNull(
                workspaceResolver, "Workspace resolver is required.");
        this.registrationGateway = Objects.requireNonNull(
                registrationGateway, "Registration gateway is required.");
        this.dummyPasswordHash = passwordService.hash(DUMMY_PASSWORD);
    }

    @Override
    public boolean isOwnerSetupRequired() {
        return userRepository.findOwner().isEmpty();
    }

    @Override
    public String getConfiguredOwnerEmail() {
        return ownerConfiguration.requireOwnerEmail();
    }

    @Override
    public synchronized UserSession createFirstOwner(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation) {
        if (!isOwnerSetupRequired()) {
            throw new AuthException("The primary OWNER is already configured.");
        }
        String configuredEmail = ownerConfiguration.requireOwnerEmail();
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        if (!configuredEmail.equals(normalizedEmail)) {
            throw new AuthException(
                    "The first OWNER email must exactly match APP_OWNER_EMAIL.");
        }
        if (password == null || passwordConfirmation == null
                || !Arrays.equals(password, passwordConfirmation)) {
            throw new AuthException("Password confirmation does not match.");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new AuthException("An account already uses this email.");
        }

        Instant now = clock.instant();
        String identifier = "usr_" + UUID.randomUUID()
                .toString().replace("-", "");
        AuthenticatedUser owner = new AuthenticatedUser(
                identifier, fullName, normalizedEmail, true,
                AuthProvider.LOCAL, "", AccountStatus.ACTIVE, now, now,
                now, Set.of(UserRole.USER, UserRole.ADMIN, UserRole.OWNER),
                studentIdentifier(normalizedEmail), "System", "BDT");
        String hash = passwordService.hash(password);
        userRepository.save(new LocalUserRecord(owner, hash, 0, null));
        migrationService.assignToFirstOwner(identifier,
                workspaceResolver.apply(identifier));
        auditRepository.append(new AuditEvent(now, identifier,
                AuditAction.OWNER_BOOTSTRAP, identifier, "SUCCESS",
                "Primary OWNER created from APP_OWNER_EMAIL."));
        return new UserSession(owner, now);
    }

    @Override
    public synchronized UserSession signInWithNsuEmail(
            String email, char[] password) {
        Instant now = clock.instant();
        LocalUserRecord record;
        try {
            String normalized = NsuEmailPolicy.requireInstitutionalEmail(email);
            record = userRepository.findByEmail(normalized).orElse(null);
        } catch (RuntimeException exception) {
            record = null;
        }
        if (record == null && registrationGateway.isConfigured()) {
            UserSession onlineSession = registrationGateway.signIn(
                    email, password);
            prepareWorkspace(onlineSession.getUser());
            auditRepository.append(new AuditEvent(now,
                    onlineSession.getUserIdentifier(),
                    AuditAction.LOGIN_SUCCESS,
                    onlineSession.getUserIdentifier(), "SUCCESS",
                    "Online password sign-in."));
            return onlineSession;
        }
        if (record == null) {
            passwordService.matches(password, dummyPasswordHash);
            auditRepository.append(new AuditEvent(now, "",
                    AuditAction.LOGIN_FAILED, "", "DENIED",
                    "Credentials or account unavailable."));
            throw new AuthException(GENERIC_SIGN_IN_FAILURE);
        }

        if (record.isLockedAt(now)) {
            auditFailed(record, now, "Temporary lockout active.");
            throw new AuthException(
                    "Too many sign-in attempts. Try again after the temporary lockout.");
        }
        boolean matches = passwordService.matches(password,
                record.passwordHash());
        if (!matches || record.user().getAccountStatus()
                != AccountStatus.ACTIVE || !record.user().isEmailVerified()) {
            int attempts = matches ? record.failedLoginAttempts()
                    : record.failedLoginAttempts() + 1;
            Instant lockedUntil = attempts >= MAXIMUM_FAILED_ATTEMPTS
                    ? now.plus(LOCK_DURATION) : null;
            if (!matches) {
                userRepository.save(record.withAuthenticationState(
                        record.user(), attempts, lockedUntil));
            }
            auditFailed(record, now, "Credentials or account unavailable.");
            throw new AuthException(GENERIC_SIGN_IN_FAILURE);
        }

        AuthenticatedUser signedInUser = record.user().withLastLogin(now);
        userRepository.save(record.withAuthenticationState(
                signedInUser, 0, null));
        prepareWorkspace(signedInUser);
        auditRepository.append(new AuditEvent(now,
                signedInUser.getUserIdentifier(), AuditAction.LOGIN_SUCCESS,
                signedInUser.getUserIdentifier(), "SUCCESS", "Local sign-in."));
        return new UserSession(signedInUser, now);
    }

    @Override
    public UserSession continueWithGoogle() {
        throw new AuthConfigurationException(
                "Google Sign-In is not configured. No authentication was performed.");
    }

    @Override
    public AuthenticatedUser registerWithNsuEmail(
            String fullName, String email, char[] password) {
        return registerWithNsuEmail(
                fullName, email, "", password, true);
    }

    @Override
    public AuthenticatedUser registerWithNsuEmail(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            boolean termsAccepted) {
        return registrationGateway.register(fullName, email,
                studentIdentifier, password, password,
                termsAccepted);
    }

    @Override
    public AuthenticatedUser verifyNsuEmail(
            String email, String verificationCode) {
        return registrationGateway.verifyEmail(email, verificationCode);
    }

    @Override
    public void resendVerification(String email) {
        registrationGateway.resendVerification(email);
    }

    @Override
    public void forgotPassword(String email) {
        throw unavailable("Password recovery");
    }

    @Override
    public void resetPassword(
            String email, String resetToken, char[] newPassword) {
        throw unavailable("Password recovery");
    }

    @Override
    public UserSession refreshSession() {
        if (registrationGateway.hasActiveSession()) {
            return registrationGateway.refreshSession();
        }
        UserSession current = sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthException("No active session."));
        LocalUserRecord record = userRepository.findById(
                current.getUserIdentifier()).orElseThrow(
                        () -> new AuthException("The signed-in account no longer exists."));
        return new UserSession(record.user(), current.getAuthenticatedAt());
    }

    @Override
    public synchronized void logout() {
        try {
            if (registrationGateway.hasActiveSession()) {
                registrationGateway.logout();
            }
            sessionManager.getCurrentSession().ifPresent(session ->
                    auditRepository.append(new AuditEvent(clock.instant(),
                            session.getUserIdentifier(), AuditAction.LOGOUT,
                            session.getUserIdentifier(), "SUCCESS",
                            "Local session ended.")));
        } finally {
            sessionManager.clearSession();
            AppPaths.clearUserDataDirectory();
        }
    }

    public synchronized void switchAccount() {
        try {
            if (registrationGateway.hasActiveSession()) {
                registrationGateway.logout();
            }
            sessionManager.getCurrentSession().ifPresent(session ->
                    auditRepository.append(new AuditEvent(clock.instant(),
                            session.getUserIdentifier(),
                            AuditAction.SWITCH_ACCOUNT,
                            session.getUserIdentifier(), "SUCCESS",
                            "Returned to sign-in without exiting.")));
        } finally {
            sessionManager.clearSession();
            AppPaths.clearUserDataDirectory();
        }
    }

    @Override
    public AuthenticatedUser getCurrentUser() {
        if (registrationGateway.hasActiveSession()) {
            return registrationGateway.getCurrentUser();
        }
        return sessionManager.getCurrentSession().map(UserSession::getUser)
                .orElseThrow(() -> new AuthException("No active session."));
    }

    public LocalUserRepository getUserRepository() {
        return userRepository;
    }

    public AuditRepository getAuditRepository() {
        return auditRepository;
    }

    public boolean verifyCurrentPassword(char[] password) {
        AuthenticatedUser user = getCurrentUser();
        return userRepository.findById(user.getUserIdentifier())
                .map(record -> passwordService.matches(
                        password, record.passwordHash()))
                .orElse(false);
    }

    private void prepareWorkspace(AuthenticatedUser user) {
        PathHolder holder = new PathHolder(
                workspaceResolver.apply(user.getUserIdentifier()));
        if (user.hasRole(UserRole.OWNER)) {
            migrationService.assignToFirstOwner(
                    user.getUserIdentifier(), holder.path());
            return;
        }
        try {
            Files.createDirectories(holder.path());
        } catch (IOException | SecurityException exception) {
            throw new AuthException(
                    "The personal finance workspace could not be opened.",
                    exception);
        }
    }

    private void auditFailed(
            LocalUserRecord record, Instant now, String reason) {
        auditRepository.append(new AuditEvent(now,
                record.user().getUserIdentifier(), AuditAction.LOGIN_FAILED,
                record.user().getUserIdentifier(), "DENIED", reason));
    }

    private static String studentIdentifier(String email) {
        int separator = email.indexOf('@');
        return separator <= 0 ? "" : email.substring(0, separator);
    }

    private static AuthConfigurationException unavailable(String feature) {
        return new AuthConfigurationException(feature
                + " requires a configured authentication backend.");
    }

    private record PathHolder(java.nio.file.Path path) {
    }
}
