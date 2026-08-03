package com.spendwise.auth.local;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AccountSession;
import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthProvider;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.AuthorizationService;
import com.spendwise.auth.OwnerConfiguration;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserRole;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.FinanceMode;
import com.spendwise.auth.admin.AdminService;
import com.spendwise.auth.audit.CsvAuditRepository;
import com.spendwise.auth.registration.RegistrationGateway;
import com.spendwise.config.AppPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class LocalAuthenticationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final String OWNER_EMAIL =
            "shibli.moon.253@northsouth.edu";
    private static final char[] OWNER_PASSWORD =
            "StrongOwner1!".toCharArray();
    private static int passed;

    private LocalAuthenticationTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("wealthora-local-auth-");
        try {
            runTests(root);
            System.out.println("All " + passed
                    + " local authentication and authorization tests passed.");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void runTests(Path root) throws Exception {
        Path legacy = root.resolve("data");
        Path workspaces = legacy.resolve("users");
        Path auth = root.resolve("auth");
        Path backups = root.resolve("backups");
        Files.createDirectories(legacy);
        byte[] expenseBytes = "date,amount\n2026-08-01,500.00\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] incomeBytes = "date,amount\n2026-08-01,1500.00\n"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(legacy.resolve("expenses.csv"), expenseBytes);
        Files.write(legacy.resolve("income.csv"), incomeBytes);
        String expenseHash = sha256(expenseBytes);
        String incomeHash = sha256(incomeBytes);

        CsvLocalUserRepository users = new CsvLocalUserRepository(
                auth.resolve("users.csv"));
        CsvAuditRepository audit = new CsvAuditRepository(
                auth.resolve("audit.csv"));
        SessionManager sessions = new SessionManager();
        PasswordService passwords = new PasswordService();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LocalDesktopAuthService service = new LocalDesktopAuthService(
                users, passwords, new OwnerConfiguration(OWNER_EMAIL),
                sessions, audit,
                new LegacyDataMigrationService(
                        legacy, backups, audit, clock),
                clock, identifier -> workspaces.resolve(identifier));

        test("missing owner configuration fails closed", () -> {
            OwnerConfiguration missing = new OwnerConfiguration("");
            expect(AuthConfigurationException.class,
                    missing::requireOwnerEmail);
        });
        test("first owner requires exact configured NSU email", () -> {
            expect(AuthException.class, () -> service.createFirstOwner(
                    "Primary Owner", "another@northsouth.edu",
                    OWNER_PASSWORD, OWNER_PASSWORD));
            assertTrue(service.isOwnerSetupRequired());
        });
        test("eight-character letter-and-number password policy", () -> {
            passwords.requireStrong("moon1234".toCharArray());
            passwords.requireStrong("wealthora25".toCharArray());
            passwords.requireStrong("student2026".toCharArray());
            passwords.requireStrong(
                    ("a1" + "x".repeat(126)).toCharArray());
            expect(AuthException.class, () -> passwords.requireStrong(
                    "12345678".toCharArray()));
            expect(AuthException.class, () -> passwords.requireStrong(
                    "abcdefgh".toCharArray()));
            expect(AuthException.class, () -> passwords.requireStrong(
                    "moon12".toCharArray()));
            expect(AuthException.class, () -> passwords.requireStrong(
                    " moon1234".toCharArray()));
            expect(AuthException.class, () -> passwords.requireStrong(
                    "moon1234 ".toCharArray()));
            expect(AuthException.class, () -> passwords.requireStrong(
                    ("a1" + "x".repeat(127)).toCharArray()));
        });

        UserSession ownerSession = service.createFirstOwner(
                "Primary Owner", OWNER_EMAIL,
                OWNER_PASSWORD, OWNER_PASSWORD);
        sessions.startSession(ownerSession);
        String ownerId = ownerSession.getUserIdentifier();
        Path ownerWorkspace = workspaces.resolve(ownerId);

        test("owner has USER ADMIN OWNER roles", () -> {
            assertTrue(ownerSession.hasRole(UserRole.USER));
            assertTrue(ownerSession.hasRole(UserRole.ADMIN));
            assertTrue(ownerSession.hasRole(UserRole.OWNER));
            assertTrue(ownerSession.canAccessAdminConsole());
            assertFalse(service.isOwnerSetupRequired());
            expect(AuthException.class, () -> service.createFirstOwner(
                    "Second Owner", OWNER_EMAIL,
                    OWNER_PASSWORD, OWNER_PASSWORD));
        });
        test("password is BCrypt-hashed and never persisted in plaintext", () -> {
            String persisted = Files.readString(
                    auth.resolve("users.csv"), StandardCharsets.UTF_8);
            assertTrue(persisted.contains("$2"));
            assertFalse(persisted.contains(new String(OWNER_PASSWORD)));
        });
        test("legacy finance data is copied byte-for-byte", () -> {
            assertEquals(expenseHash,
                    sha256(Files.readAllBytes(
                            ownerWorkspace.resolve("expenses.csv"))));
            assertEquals(incomeHash,
                    sha256(Files.readAllBytes(
                            ownerWorkspace.resolve("income.csv"))));
            assertEquals(expenseHash,
                    sha256(Files.readAllBytes(legacy.resolve("expenses.csv"))));
            assertEquals(incomeHash,
                    sha256(Files.readAllBytes(legacy.resolve("income.csv"))));
            try (var backupFiles = Files.list(backups)) {
                assertEquals(1L, backupFiles.count());
            }
        });
        test("Google sign-in never succeeds without backend", () ->
                expect(AuthConfigurationException.class,
                        service::continueWithGoogle));
        test("online NSU user uses server session without replacing owner", () -> {
            RecordingGateway gateway = new RecordingGateway();
            SessionManager onlineSessions = new SessionManager();
            LocalDesktopAuthService hybrid = new LocalDesktopAuthService(
                    users, passwords, new OwnerConfiguration(OWNER_EMAIL),
                    onlineSessions, audit,
                    new LegacyDataMigrationService(
                            legacy, backups, audit, clock),
                    clock, identifier -> workspaces.resolve(identifier),
                    gateway);
            UserSession remote = hybrid.signInWithNsuEmail(
                    gateway.user.getEmail(),
                    "RemoteStudent1!".toCharArray());
            onlineSessions.startSession(remote);
            assertTrue(gateway.active);
            assertEquals(FinanceMode.CLOUD, remote.getFinanceMode());
            assertFalse(Files.exists(workspaces.resolve(
                    gateway.user.getUserIdentifier())));
            assertEquals(gateway.user.getUserIdentifier(),
                    hybrid.refreshSession().getUserIdentifier());
            hybrid.logout();
            assertTrue(gateway.loggedOut);
            assertTrue(onlineSessions.getCurrentSession().isEmpty());
            assertTrue(users.findOwner().isPresent());
        });
        test("explicit CLOUD sign-in bypasses a same-email local account", () -> {
            RecordingGateway gateway = new RecordingGateway(OWNER_EMAIL);
            LocalDesktopAuthService hybrid = new LocalDesktopAuthService(
                    users, passwords, new OwnerConfiguration(OWNER_EMAIL),
                    new SessionManager(), audit,
                    new LegacyDataMigrationService(
                            legacy, backups, audit, clock),
                    clock, identifier -> workspaces.resolve(identifier),
                    gateway);
            UserSession cloud = hybrid.signInWithNsuEmail(
                    OWNER_EMAIL, "CloudPassword1!".toCharArray(),
                    FinanceMode.CLOUD);
            assertEquals(FinanceMode.CLOUD, cloud.getFinanceMode());
            assertTrue(gateway.active);

            UserSession local = hybrid.signInWithNsuEmail(
                    OWNER_EMAIL, OWNER_PASSWORD, FinanceMode.LOCAL);
            assertEquals(FinanceMode.LOCAL, local.getFinanceMode());
            assertTrue(local.isOwner());
        });

        AppPaths.activateUserDataDirectory(ownerId);
        service.logout();
        test("logout clears the current session and private workspace state", () -> {
            assertTrue(sessions.getCurrentSession().isEmpty());
            assertEquals(AppPaths.getLegacyDataDirectory(),
                    AppPaths.getDataDirectory());
        });
        UserSession signedIn = service.signInWithNsuEmail(
                OWNER_EMAIL, OWNER_PASSWORD);
        sessions.startSession(signedIn);
        test("owner signs in and lands with owner capability", () -> {
            assertTrue(signedIn.isOwner());
            assertEquals(ownerId, signedIn.getUserIdentifier());
        });

        AuthenticatedUser normalUser = new AuthenticatedUser(
                "usr_normal_1", "Normal User", "normal@northsouth.edu",
                true, AuthProvider.LOCAL, "", AccountStatus.ACTIVE,
                NOW, NOW, null, Set.of(UserRole.USER), "normal", "System",
                "BDT");
        users.save(new LocalUserRecord(normalUser,
                passwords.hash("NormalUser1!Password".toCharArray()), 0, null));
        AdminService admin = new AdminService(users, audit, service);
        UserSession normalSession = new UserSession(normalUser, NOW);

        test("USER cannot access Admin Console", () ->
                expect(AuthException.class,
                        () -> admin.getOverview(normalSession)));
        test("only OWNER can grant ADMIN", () -> {
            expect(AuthException.class, () -> admin.grantAdministrator(
                    normalSession, normalUser.getUserIdentifier(),
                    "NormalUser1!Password".toCharArray(), "Unauthorized"));
            AuthenticatedUser promoted = admin.grantAdministrator(
                    signedIn, normalUser.getUserIdentifier(), OWNER_PASSWORD,
                    "Approved support duty");
            assertTrue(promoted.hasRole(UserRole.ADMIN));
        });
        test("ADMIN cannot access another user's finance workspace", () -> {
            AuthenticatedUser administrator = users.findById(
                    normalUser.getUserIdentifier()).orElseThrow().user();
            assertTrue(administrator.hasRole(UserRole.ADMIN));
            AuthorizationService authorization = new AuthorizationService();
            authorization.requireOwnWorkspace(
                    new UserSession(administrator, NOW),
                    administrator.getUserIdentifier());
            expect(AuthException.class, () -> authorization
                    .requireOwnWorkspace(new UserSession(administrator, NOW),
                            ownerId));
        });
        test("OWNER cannot be demoted", () ->
                expect(AuthException.class, () ->
                        admin.revokeAdministrator(signedIn, ownerId,
                                OWNER_PASSWORD, "Invalid owner demotion")));
        test("OWNER can revoke ADMIN", () -> {
            AuthenticatedUser demoted = admin.revokeAdministrator(
                    signedIn, normalUser.getUserIdentifier(), OWNER_PASSWORD,
                    "Support duty ended");
            assertFalse(demoted.hasRole(UserRole.ADMIN));
        });
        test("local session can be listed and revoked", () -> {
            sessions.startSession(normalSession);
            List<AccountSession> activeSessions = service.listSessions();
            assertEquals(1, activeSessions.size());
            assertTrue(activeSessions.get(0).currentSession());
            service.revokeSession(activeSessions.get(0));
            assertTrue(sessions.getCurrentSession().isEmpty());
            assertTrue(users.findOwner().isPresent());
        });
        test("local password change revokes session and preserves owner", () -> {
            char[] oldPassword = "NormalUser1!Password".toCharArray();
            char[] newPassword = "ChangedNormal2!Password".toCharArray();
            UserSession current = service.signInWithNsuEmail(
                    normalUser.getEmail(), oldPassword);
            sessions.startSession(current);
            service.changePassword(oldPassword, newPassword);
            assertTrue(sessions.getCurrentSession().isEmpty());
            expect(AuthException.class, () -> service.signInWithNsuEmail(
                    normalUser.getEmail(), oldPassword));
            assertEquals(normalUser.getUserIdentifier(),
                    service.signInWithNsuEmail(
                            normalUser.getEmail(), newPassword)
                            .getUserIdentifier());
            assertTrue(users.findOwner().isPresent());
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
        });
        test("finance workspace ownership rejects cross-user access", () -> {
            AuthorizationService authorization = new AuthorizationService();
            authorization.requireOwnWorkspace(signedIn, ownerId);
            expect(AuthException.class, () -> authorization
                    .requireOwnWorkspace(signedIn,
                            normalUser.getUserIdentifier()));
        });
        test("owner and finance assignment persist across restart", () -> {
            SessionManager restartedSessions = new SessionManager();
            LocalDesktopAuthService restarted = new LocalDesktopAuthService(
                    new CsvLocalUserRepository(auth.resolve("users.csv")),
                    new PasswordService(), new OwnerConfiguration(OWNER_EMAIL),
                    restartedSessions, audit,
                    new LegacyDataMigrationService(
                            legacy, backups, audit, clock),
                    clock, identifier -> workspaces.resolve(identifier));
            assertFalse(restarted.isOwnerSetupRequired());
            UserSession restored = restarted.signInWithNsuEmail(
                    OWNER_EMAIL, OWNER_PASSWORD);
            assertEquals(ownerId, restored.getUserIdentifier());
            try (var backupFiles = Files.list(backups)) {
                assertEquals(1L, backupFiles.count());
            }
        });
        test("switch account clears the previous session", () -> {
            sessions.startSession(signedIn);
            AppPaths.activateUserDataDirectory(ownerId);
            service.switchAccount();
            assertTrue(sessions.getCurrentSession().isEmpty());
            assertEquals(AppPaths.getLegacyDataDirectory(),
                    AppPaths.getDataDirectory());
        });
        test("failed sign-ins trigger temporary lockout", () -> {
            for (int count = 0;
                    count < LocalDesktopAuthService.MAXIMUM_FAILED_ATTEMPTS;
                    count++) {
                expect(AuthException.class, () -> service.signInWithNsuEmail(
                        OWNER_EMAIL, "WrongPassword1!".toCharArray()));
            }
            expect(AuthException.class, () -> service.signInWithNsuEmail(
                    OWNER_EMAIL, OWNER_PASSWORD));
            LocalUserRecord locked = users.findById(ownerId).orElseThrow();
            assertTrue(locked.lockedUntil() != null);
        });
        test("security actions are audited", () -> {
            assertTrue(audit.findAll().stream().anyMatch(event ->
                    event.action().name().equals("LOGOUT")));
            assertTrue(audit.findAll().stream().anyMatch(event ->
                    event.action().name().equals("SWITCH_ACCOUNT")));
            assertTrue(audit.findAll().stream().anyMatch(event ->
                    event.action().name().equals("ADMIN_GRANTED")));
            assertTrue(audit.findAll().stream().anyMatch(event ->
                    event.action().name().equals("LEGACY_DATA_ASSIGNED")));
        });
        Arrays.fill(OWNER_PASSWORD, '\0');
    }

    private static String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static void test(String name, ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void expect(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName()
                    + " but caught " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + ".");
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true.");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false.");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected <" + expected
                    + "> but was <" + actual + ">.");
        }
    }

    private static final class RecordingGateway
            implements RegistrationGateway {

        private final AuthenticatedUser user;
        private boolean active;
        private boolean loggedOut;

        private RecordingGateway() {
            this("remote.student@northsouth.edu");
        }

        private RecordingGateway(String email) {
            user = new AuthenticatedUser(
                    "usr_remote_1", "Remote Student", email, true,
                    AuthProvider.LOCAL, "", AccountStatus.ACTIVE,
                    NOW, NOW, NOW, Set.of(UserRole.USER), "2530000003",
                    "System", "BDT");
        }

        @Override
        public AuthenticatedUser register(
                String fullName, String email, String studentIdentifier,
                char[] password, char[] passwordConfirmation,
                boolean termsAccepted) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthenticatedUser verifyEmail(
                String email, String verificationCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resendVerification(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forgotPassword(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetPassword(
                String email, String resetToken, char[] newPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void changePassword(
                char[] currentPassword, char[] newPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPassword(char[] newPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AccountSession> listSessions() {
            return List.of(new AccountSession(
                    "session-remote", "Test Desktop", NOW,
                    NOW.plusSeconds(900), true));
        }

        @Override
        public void revokeSession(AccountSession session) {
            if (session.currentSession()) active = false;
        }

        @Override
        public void logoutAll() {
            active = false;
            loggedOut = true;
        }

        @Override
        public UserSession signIn(String email, char[] password) {
            active = true;
            loggedOut = false;
            return new UserSession(user, NOW, FinanceMode.CLOUD);
        }

        @Override
        public UserSession refreshSession() {
            if (!active) throw new AuthException("No active session.");
            return new UserSession(user, NOW, FinanceMode.CLOUD);
        }

        @Override
        public void logout() {
            active = false;
            loggedOut = true;
        }

        @Override
        public AuthenticatedUser getCurrentUser() {
            return user;
        }

        @Override
        public boolean hasActiveSession() {
            return active;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
