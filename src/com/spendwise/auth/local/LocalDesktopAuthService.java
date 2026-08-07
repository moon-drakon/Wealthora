package com.spendwise.auth.local;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthenticationAvailability;
import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthProvider;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.EmailAddressPolicy;
import com.spendwise.auth.FinanceMode;
import com.spendwise.auth.LocalAccountService;
import com.spendwise.auth.NsuEmailPolicy;
import com.spendwise.auth.OwnerConfiguration;
import com.spendwise.auth.OwnerSetupService;
import com.spendwise.auth.PasswordRecoveryChallenge;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.RecoveryAnswerService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserRole;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.audit.AuditAction;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.auth.audit.AuditRepository;
import com.spendwise.auth.registration.RegistrationGateway;
import com.spendwise.auth.registration.FinanceApiGateway;
import com.spendwise.auth.admin.AdministrationGateway;
import com.spendwise.auth.registration.UnconfiguredRegistrationGateway;
import com.spendwise.config.AppPaths;
import com.spendwise.voice.SpeechApiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class LocalDesktopAuthService
        implements AuthService, OwnerSetupService, LocalAccountService {

    public static final int MAXIMUM_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String GENERIC_SIGN_IN_FAILURE =
            "Email or password is incorrect, or the account is unavailable.";
    private static final char[] DUMMY_PASSWORD =
            "NotAReal1!Password".toCharArray();

    private final LocalUserRepository userRepository;
    private final PasswordService passwordService;
    private final RecoveryAnswerService recoveryAnswerService;
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
        this.recoveryAnswerService = new RecoveryAnswerService(passwordService);
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

    public SpeechApiClient getSpeechApiClient() {
        return registrationGateway;
    }

    public FinanceApiGateway getFinanceApiGateway() {
        return registrationGateway;
    }

    @Override
    public AuthenticationAvailability getAuthenticationAvailability() {
        return registrationGateway.getAuthenticationAvailability();
    }

    public AdministrationGateway getAdministrationGateway() {
        return registrationGateway instanceof AdministrationGateway gateway
                ? gateway : AdministrationGateway.unavailable();
    }

    @Override
    public String getConfiguredOwnerEmail() {
        return ownerConfiguration.getConfiguredEmail();
    }

    @Override
    public synchronized UserSession createFirstOwner(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation) {
        return createFirstOwnerRecord(fullName, email, password,
                passwordConfirmation, null);
    }

    @Override
    public synchronized UserSession createFirstOwner(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer) {
        return createFirstOwnerRecord(fullName, email, password,
                passwordConfirmation, requireRecovery(recoveryQuestion,
                        recoveryHint, recoveryAnswer));
    }

    private UserSession createFirstOwnerRecord(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation,
            RecoveryData recovery) {
        if (!isOwnerSetupRequired()) {
            throw new AuthException("The primary OWNER is already configured.");
        }
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        if (ownerConfiguration.isConfigured()
                && !ownerConfiguration.requireOwnerEmail().equals(
                        normalizedEmail)) {
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
        userRepository.save(recovery == null
                ? new LocalUserRecord(owner, hash, 0, null)
                : new LocalUserRecord(owner, hash, 0, null,
                        recovery.question(), recovery.hint(),
                        recovery.answerHash()));
        migrationService.assignToFirstOwner(identifier,
                workspaceResolver.apply(identifier));
        auditRepository.append(new AuditEvent(now, identifier,
                AuditAction.OWNER_BOOTSTRAP, identifier, "SUCCESS",
                "Primary local OWNER created."));
        return new UserSession(owner, now);
    }

    @Override
    public synchronized AuthenticatedUser registerLocalAccount(
            String fullName,
            String email,
            String studentIdentifier,
            char[] password,
            char[] passwordConfirmation,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer) {
        if (isOwnerSetupRequired()) {
            throw new AuthException(
                    "Complete the primary OWNER setup before adding users.");
        }
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        if (password == null || passwordConfirmation == null
                || !Arrays.equals(password, passwordConfirmation)) {
            throw new AuthException("Password confirmation does not match.");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new AuthException("An account already uses this email.");
        }
        String studentId = studentIdentifier == null
                ? "" : studentIdentifier.strip();
        if (studentId.length() > 40) {
            throw new AuthException(
                    "Student ID must not exceed 40 characters.");
        }
        RecoveryData recovery = requireRecovery(
                recoveryQuestion, recoveryHint, recoveryAnswer);
        Instant now = clock.instant();
        String identifier = "usr_" + UUID.randomUUID()
                .toString().replace("-", "");
        AuthenticatedUser user = new AuthenticatedUser(
                identifier, fullName, normalizedEmail, true,
                AuthProvider.LOCAL, "", AccountStatus.ACTIVE, now, now,
                null, Set.of(UserRole.USER),
                studentId.isEmpty() ? studentIdentifier(normalizedEmail)
                        : studentId,
                "System", "BDT");
        userRepository.save(new LocalUserRecord(user,
                passwordService.hash(password), 0, null,
                recovery.question(), recovery.hint(),
                recovery.answerHash()));
        prepareWorkspace(user);
        auditRepository.append(new AuditEvent(now, identifier,
                AuditAction.REGISTRATION_CREATED, identifier, "SUCCESS",
                "Local user account created on this computer."));
        return user;
    }

    @Override
    public synchronized PasswordRecoveryChallenge
            getPasswordRecoveryChallenge(String email) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        LocalUserRecord record = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AuthException(
                        "No recoverable local account uses this email."));
        if (!record.hasPasswordRecovery()) {
            throw new AuthException(
                    "Recovery is not configured for this account. Ask the OWNER or an administrator to reset the password.");
        }
        auditRepository.append(new AuditEvent(clock.instant(), "",
                AuditAction.PASSWORD_RESET_REQUESTED,
                record.user().getUserIdentifier(), "SUCCESS",
                "Local recovery challenge opened."));
        return new PasswordRecoveryChallenge(
                record.recoveryQuestion(), record.recoveryHint());
    }

    @Override
    public synchronized void resetPasswordWithRecovery(
            String email,
            char[] recoveryAnswer,
            char[] newPassword,
            char[] passwordConfirmation) {
        String normalizedEmail = NsuEmailPolicy.requireInstitutionalEmail(email);
        LocalUserRecord record = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AuthException(
                        "The recovery answer or account is incorrect."));
        Instant now = clock.instant();
        if (!record.hasPasswordRecovery()) {
            throw new AuthException(
                    "Recovery is not configured for this account. Ask the OWNER or an administrator to reset the password.");
        }
        if (record.isLockedAt(now)) {
            throw new AuthException(
                    "Too many attempts. Try again after the temporary lockout.");
        }
        if (!recoveryAnswerService.matches(
                recoveryAnswer, record.recoveryAnswerHash())) {
            int attempts = record.failedLoginAttempts() + 1;
            Instant lockedUntil = attempts >= MAXIMUM_FAILED_ATTEMPTS
                    ? now.plus(LOCK_DURATION) : null;
            userRepository.save(record.withAuthenticationState(
                    record.user(), attempts, lockedUntil));
            auditRepository.append(new AuditEvent(now, "",
                    AuditAction.PASSWORD_RESET_REQUESTED,
                    record.user().getUserIdentifier(), "DENIED",
                    "Incorrect local recovery answer."));
            throw new AuthException(
                    "The recovery answer or account is incorrect.");
        }
        requireMatchingNewPassword(newPassword, passwordConfirmation,
                record.passwordHash());
        userRepository.save(record.withPasswordHash(
                passwordService.hash(newPassword)));
        auditRepository.append(new AuditEvent(now,
                record.user().getUserIdentifier(),
                AuditAction.PASSWORD_RESET_COMPLETED,
                record.user().getUserIdentifier(), "SUCCESS",
                "Password reset with the protected local recovery answer."));
    }

    @Override
    public synchronized boolean hasPasswordRecovery(UserSession session) {
        UserSession required = Objects.requireNonNull(
                session, "User session is required.");
        return userRepository.findById(required.getUserIdentifier())
                .map(LocalUserRecord::hasPasswordRecovery)
                .orElse(false);
    }

    @Override
    public synchronized void updatePasswordRecovery(
            UserSession session,
            char[] currentPassword,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer,
            char[] recoveryAnswerConfirmation) {
        UserSession required = Objects.requireNonNull(
                session, "User session is required.");
        UserSession current = sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthException("No active session."));
        if (!current.getUserIdentifier().equals(
                required.getUserIdentifier())) {
            throw new AuthException("The signed-in session is no longer current.");
        }
        LocalUserRecord record = userRepository.findById(
                required.getUserIdentifier()).orElseThrow(
                        () -> new AuthException(
                                "The signed-in account no longer exists."));
        if (!passwordService.matches(currentPassword, record.passwordHash())) {
            throw new AuthException("The current password is incorrect.");
        }
        if (recoveryAnswer == null || recoveryAnswerConfirmation == null
                || !Arrays.equals(
                        recoveryAnswer, recoveryAnswerConfirmation)) {
            throw new AuthException(
                    "Recovery answer confirmation does not match.");
        }
        RecoveryData recovery = requireRecovery(
                recoveryQuestion, recoveryHint, recoveryAnswer);
        userRepository.save(record.withPasswordRecovery(
                recovery.question(), recovery.hint(),
                recovery.answerHash()));
        auditRepository.append(new AuditEvent(clock.instant(),
                required.getUserIdentifier(), AuditAction.RECOVERY_CONFIGURED,
                required.getUserIdentifier(), "SUCCESS",
                record.hasPasswordRecovery()
                        ? "Local password recovery updated."
                        : "Local password recovery configured."));
    }

    @Override
    public synchronized UserSession signInWithNsuEmail(
            String email, char[] password) {
        Instant now = clock.instant();
        LocalUserRecord record = findLocalRecord(email);
        if (record == null && registrationGateway.isConfigured()) {
            return signInOnline(email, password, now);
        }
        return signInLocally(password, record, now);
    }

    @Override
    public synchronized UserSession signInWithNsuEmail(
            String email, char[] password, FinanceMode destination) {
        FinanceMode requiredDestination = Objects.requireNonNull(
                destination, "A sign-in destination is required.");
        Instant now = clock.instant();
        if (requiredDestination == FinanceMode.CLOUD) {
            if (!registrationGateway.isConfigured()) {
                throw unavailable("Cloud password sign-in");
            }
            return signInOnline(email, password, now);
        }
        return signInLocally(password, findLocalRecord(email), now);
    }

    private LocalUserRecord findLocalRecord(String email) {
        try {
            String normalized = NsuEmailPolicy.requireInstitutionalEmail(email);
            return userRepository.findByEmail(normalized).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private UserSession signInOnline(
            String email, char[] password, Instant now) {
        UserSession onlineSession = registrationGateway.signIn(
                email, password);
        auditRepository.append(new AuditEvent(now,
                onlineSession.getUserIdentifier(),
                AuditAction.LOGIN_SUCCESS,
                onlineSession.getUserIdentifier(), "SUCCESS",
                "Online password sign-in."));
        return onlineSession;
    }

    private UserSession signInLocally(
            char[] password, LocalUserRecord record, Instant now) {
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
        if (!registrationGateway.isConfigured()) {
            throw unavailable("Google Sign-In");
        }
        UserSession session = registrationGateway.continueWithGoogle();
        auditRepository.append(new AuditEvent(clock.instant(),
                session.getUserIdentifier(), AuditAction.LOGIN_SUCCESS,
                session.getUserIdentifier(), "SUCCESS",
                "Verified Google browser sign-in."));
        return session;
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
        if (!registrationGateway.isConfigured()) {
            throw unavailable("Password recovery");
        }
        registrationGateway.forgotPassword(email);
    }

    @Override
    public void resetPassword(
            String email, String resetToken, char[] newPassword) {
        if (!registrationGateway.isConfigured()) {
            throw unavailable("Password recovery");
        }
        registrationGateway.resetPassword(email, resetToken, newPassword);
    }

    @Override
    public synchronized void changePassword(
            char[] currentPassword, char[] newPassword) {
        if (registrationGateway.hasActiveSession()) {
            registrationGateway.changePassword(currentPassword, newPassword);
            clearDesktopSession();
            return;
        }
        UserSession current = sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthException("No active session."));
        LocalUserRecord record = userRepository.findById(
                current.getUserIdentifier()).orElseThrow(
                        () -> new AuthException(
                                "The signed-in account no longer exists."));
        if (!passwordService.matches(currentPassword, record.passwordHash())) {
            throw new AuthException("The current password is incorrect.");
        }
        passwordService.requireStrong(newPassword);
        if (passwordService.matches(newPassword, record.passwordHash())) {
            throw new AuthException(
                    "Choose a password different from the current password.");
        }
        userRepository.save(record.withPasswordHash(
                passwordService.hash(newPassword)));
        auditRepository.append(new AuditEvent(clock.instant(),
                current.getUserIdentifier(), AuditAction.PASSWORD_CHANGED,
                current.getUserIdentifier(), "SUCCESS",
                "Local password changed and session revoked."));
        clearDesktopSession();
    }

    @Override
    public synchronized void setPassword(char[] newPassword) {
        if (!registrationGateway.hasActiveSession()) {
            throw new AuthException(
                    "This local account already has a password.");
        }
        registrationGateway.setPassword(newPassword);
        clearDesktopSession();
    }

    @Override
    public synchronized List<AccountSession> listSessions() {
        if (registrationGateway.hasActiveSession()) {
            return registrationGateway.listSessions();
        }
        UserSession current = sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthException("No active session."));
        return List.of(new AccountSession(
                localSessionIdentifier(current),
                "This Windows application",
                current.getAuthenticatedAt(), null, true));
    }

    @Override
    public synchronized void revokeSession(AccountSession session) {
        AccountSession required = Objects.requireNonNull(
                session, "Session is required.");
        if (registrationGateway.hasActiveSession()) {
            registrationGateway.revokeSession(required);
            if (required.currentSession()) clearDesktopSession();
            return;
        }
        UserSession current = sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthException("No active session."));
        if (!localSessionIdentifier(current).equals(
                required.sessionIdentifier())) {
            throw new AuthException("The selected session is unavailable.");
        }
        auditRepository.append(new AuditEvent(clock.instant(),
                current.getUserIdentifier(), AuditAction.SESSION_REVOKED,
                current.getUserIdentifier(), "SUCCESS",
                "Local desktop session revoked."));
        clearDesktopSession();
    }

    @Override
    public synchronized void logoutAll() {
        try {
            if (registrationGateway.hasActiveSession()) {
                registrationGateway.logoutAll();
            }
        } finally {
            clearDesktopSession();
        }
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

    public synchronized void resetPasswordByAdministrator(
            UserSession actor,
            String targetUserIdentifier,
            char[] actorPassword,
            char[] newPassword,
            char[] passwordConfirmation) {
        Objects.requireNonNull(actor, "Administrator session is required.");
        if (!actor.canAccessAdminConsole()) {
            throw new AuthException(
                    "Administrator access is required to reset a password.");
        }
        if (registrationGateway.hasActiveSession()) {
            throw new AuthException(
                    "Administrator password reset is available for local accounts only.");
        }
        UserSession current = sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthException("No active session."));
        if (!current.getUserIdentifier().equals(actor.getUserIdentifier())) {
            throw new AuthException(
                    "The administrator session is no longer current.");
        }
        LocalUserRecord target = userRepository.findById(
                Objects.requireNonNull(targetUserIdentifier).strip())
                .orElseThrow(() -> new AuthException(
                        "The selected user no longer exists."));
        if (target.user().hasRole(UserRole.OWNER)) {
            throw new AuthException(
                    "The primary OWNER password must be changed from Security or recovered with its answer.");
        }
        if (target.user().hasRole(UserRole.ADMIN) && !actor.isOwner()) {
            throw new AuthException(
                    "Only the OWNER can reset an administrator password.");
        }
        if (target.user().getUserIdentifier().equals(
                actor.getUserIdentifier())) {
            throw new AuthException(
                    "Use Security settings to change your own password.");
        }
        if (!verifyCurrentPassword(actorPassword)) {
            throw new AuthException(
                    "Administrator password confirmation failed.");
        }
        requireMatchingNewPassword(newPassword, passwordConfirmation,
                target.passwordHash());
        userRepository.save(target.withPasswordHash(
                passwordService.hash(newPassword)));
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

    private void clearDesktopSession() {
        sessionManager.clearSession();
        AppPaths.clearUserDataDirectory();
    }

    private static String localSessionIdentifier(UserSession session) {
        return "local:" + session.getUserIdentifier();
    }

    private void auditFailed(
            LocalUserRecord record, Instant now, String reason) {
        auditRepository.append(new AuditEvent(now,
                record.user().getUserIdentifier(), AuditAction.LOGIN_FAILED,
                record.user().getUserIdentifier(), "DENIED", reason));
    }

    private RecoveryData requireRecovery(
            String question, String hint, char[] answer) {
        String requiredQuestion = requiredText(
                question, "Recovery question", 8, 120);
        String requiredHint = requiredText(
                hint, "Recovery hint", 3, 120);
        String normalizedAnswer = recoveryAnswerService
                .normalizeForComparison(answer);
        String normalizedHint = requiredHint.toLowerCase(
                java.util.Locale.ROOT).replaceAll("\\s+", " ");
        if (normalizedHint.contains(normalizedAnswer)) {
            throw new AuthException(
                    "Recovery hint must not reveal the recovery answer.");
        }
        return new RecoveryData(requiredQuestion, requiredHint,
                recoveryAnswerService.hash(answer));
    }

    private void requireMatchingNewPassword(
            char[] password, char[] confirmation, String currentHash) {
        if (password == null || confirmation == null
                || !Arrays.equals(password, confirmation)) {
            throw new AuthException("Password confirmation does not match.");
        }
        passwordService.requireStrong(password);
        if (passwordService.matches(password, currentHash)) {
            throw new AuthException(
                    "Choose a password different from the current password.");
        }
    }

    private static String requiredText(
            String value, String name, int minimum, int maximum) {
        String required = value == null ? "" : value.strip();
        if (required.length() < minimum) {
            throw new AuthException(name + " must contain at least "
                    + minimum + " characters.");
        }
        if (required.length() > maximum) {
            throw new AuthException(name + " must not exceed "
                    + maximum + " characters.");
        }
        return required;
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

    private record RecoveryData(
            String question, String hint, String answerHash) {
    }
}
