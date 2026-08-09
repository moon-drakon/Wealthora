package com.spendwise.auth.local;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.OwnerConfiguration;
import com.spendwise.auth.PasswordService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.audit.AuditEvent;
import com.spendwise.auth.audit.AuditRepository;
import com.spendwise.auth.otp.EmailOtpChallenge;
import com.spendwise.auth.otp.EmailVerificationGateway;
import com.spendwise.auth.otp.OtpPurpose;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EmailOtpAccountServiceTest {

    private static final String OWNER_EMAIL = "owner@northsouth.edu";
    private static final String USER_EMAIL = "student@northsouth.edu";
    private static final char[] OWNER_PASSWORD = "OwnerPass1".toCharArray();
    private static final char[] USER_PASSWORD = "StudentPass1".toCharArray();

    private EmailOtpAccountServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("wealthora-otp-account-");
        try {
            registrationRequiresVerifiedOtp(root.resolve("registration"));
            cancellationExpiryAndDeliveryFailure(root.resolve("failure"));
            resendUsesOnlyLatestChallenge(root.resolve("resend"));
            passwordResetChangesOnlyLocalHash(root.resolve("reset"));
            saveFailurePreservesOldPassword(root.resolve("rollback"));
            System.out.println("EmailOtpAccountServiceTest passed");
        } finally {
            deleteTree(root);
        }
    }

    private static void registrationRequiresVerifiedOtp(Path root) {
        Fixture fixture = new Fixture(root);
        EmailOtpChallenge challenge = fixture.beginRegistration();
        check(fixture.repository.findByEmail(USER_EMAIL).isEmpty(),
                "account must not exist before verification");
        check(fixture.gateway.lastPurpose == OtpPurpose.REGISTRATION,
                "registration purpose");
        check(fixture.gateway.lastEmail.equals(USER_EMAIL),
                "normalized email");
        expect(AuthException.class, () -> fixture.service.verifyRegistration(
                challenge.challengeIdentifier(), "000000"));
        check(fixture.repository.findByEmail(USER_EMAIL).isEmpty(),
                "invalid code must not create account");

        AuthenticatedUser user = fixture.service.verifyRegistration(
                challenge.challengeIdentifier(), fixture.gateway.currentCode);
        check(user.isEmailVerified(), "verified account");
        check(fixture.repository.findByEmail(USER_EMAIL).isPresent(),
                "verified account persisted");
        check(fixture.service.signInWithNsuEmail(
                USER_EMAIL, USER_PASSWORD.clone()).getEmail()
                .equals(USER_EMAIL), "new account sign-in");
        expect(AuthException.class, () -> fixture.service.verifyRegistration(
                challenge.challengeIdentifier(), fixture.gateway.currentCode));
        check(fixture.audit.events.stream().noneMatch(event ->
                event.reason().contains(fixture.gateway.currentCode)),
                "OTP absent from audit");
    }

    private static void cancellationExpiryAndDeliveryFailure(Path root) {
        Fixture fixture = new Fixture(root);
        EmailOtpChallenge cancelled = fixture.beginRegistration();
        fixture.service.cancelRegistration(cancelled.challengeIdentifier());
        expect(AuthException.class, () -> fixture.service.verifyRegistration(
                cancelled.challengeIdentifier(), fixture.gateway.currentCode));
        check(fixture.repository.findByEmail(USER_EMAIL).isEmpty(),
                "cancelled registration");

        EmailOtpChallenge expired = fixture.beginRegistration();
        fixture.clock.advance(Duration.ofMinutes(11));
        expect(AuthException.class, () -> fixture.service.verifyRegistration(
                expired.challengeIdentifier(), fixture.gateway.currentCode));
        check(fixture.repository.findByEmail(USER_EMAIL).isEmpty(),
                "expired registration");

        fixture.gateway.failDelivery = true;
        expect(AuthException.class, fixture::beginRegistration);
        check(fixture.repository.findByEmail(USER_EMAIL).isEmpty(),
                "delivery failure");
        check(fixture.service.signInWithNsuEmail(
                OWNER_EMAIL, OWNER_PASSWORD.clone()).isOwner(),
                "relay failure cannot affect existing sign-in");
    }

    private static void resendUsesOnlyLatestChallenge(Path root) {
        Fixture fixture = new Fixture(root);
        EmailOtpChallenge first = fixture.beginRegistration();
        String firstCode = fixture.gateway.currentCode;
        fixture.clock.advance(Duration.ofSeconds(60));
        EmailOtpChallenge replacement = fixture.service.resendRegistration(
                first.challengeIdentifier());
        check(!replacement.challengeIdentifier().equals(
                first.challengeIdentifier()), "replacement identifier");
        expect(AuthException.class, () -> fixture.service.verifyRegistration(
                first.challengeIdentifier(), firstCode));
        fixture.service.verifyRegistration(replacement.challengeIdentifier(),
                fixture.gateway.currentCode);
        check(fixture.repository.findByEmail(USER_EMAIL).isPresent(),
                "replacement code creates account");
    }

    private static void passwordResetChangesOnlyLocalHash(Path root) {
        Fixture fixture = new Fixture(root);
        fixture.completeRegistration();
        EmailOtpChallenge reset = fixture.service.beginPasswordReset(USER_EMAIL);
        check(fixture.gateway.lastPurpose == OtpPurpose.PASSWORD_RESET,
                "reset purpose");
        expect(AuthException.class, () -> fixture.service.completePasswordReset(
                reset.challengeIdentifier(), "000000",
                "NewStudent2".toCharArray(), "NewStudent2".toCharArray()));
        check(fixture.service.signInWithNsuEmail(
                USER_EMAIL, USER_PASSWORD.clone()).getEmail()
                .equals(USER_EMAIL), "invalid OTP preserves old password");

        fixture.service.completePasswordReset(reset.challengeIdentifier(),
                fixture.gateway.currentCode, "NewStudent2".toCharArray(),
                "NewStudent2".toCharArray());
        expect(AuthException.class, () -> fixture.service.signInWithNsuEmail(
                USER_EMAIL, USER_PASSWORD.clone()));
        check(fixture.service.signInWithNsuEmail(USER_EMAIL,
                "NewStudent2".toCharArray()).getEmail().equals(USER_EMAIL),
                "new password works");
        expect(AuthException.class, () -> fixture.service.completePasswordReset(
                reset.challengeIdentifier(), fixture.gateway.currentCode,
                "AnotherPass3".toCharArray(),
                "AnotherPass3".toCharArray()));
    }

    private static void saveFailurePreservesOldPassword(Path root) {
        Fixture fixture = new Fixture(root);
        fixture.completeRegistration();
        EmailOtpChallenge reset = fixture.service.beginPasswordReset(USER_EMAIL);
        fixture.repository.failWrites = true;
        expect(IllegalStateException.class,
                () -> fixture.service.completePasswordReset(
                        reset.challengeIdentifier(), fixture.gateway.currentCode,
                        "NewStudent2".toCharArray(),
                        "NewStudent2".toCharArray()));
        fixture.repository.failWrites = false;
        check(fixture.service.signInWithNsuEmail(
                USER_EMAIL, USER_PASSWORD.clone()).getEmail()
                .equals(USER_EMAIL), "failed save preserves old hash");
    }

    private static void expect(
            Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Unexpected exception", failure);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class Fixture {
        final MutableClock clock = new MutableClock(
                Instant.parse("2026-08-08T00:00:00Z"));
        final MemoryAuditRepository audit = new MemoryAuditRepository();
        final FailingRepository repository;
        final FakeGateway gateway;
        final LocalDesktopAuthService service;

        Fixture(Path root) {
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            repository = new FailingRepository(new CsvLocalUserRepository(
                    root.resolve("auth").resolve("users.csv")));
            gateway = new FakeGateway(clock);
            service = new LocalDesktopAuthService(
                    repository, new PasswordService(),
                    new OwnerConfiguration(""), new SessionManager(), audit,
                    new LegacyDataMigrationService(root.resolve("legacy"),
                            root.resolve("backups"), audit),
                    clock, root.resolve("users")::resolve, gateway);
            service.createFirstOwner("Primary Owner", OWNER_EMAIL,
                    OWNER_PASSWORD.clone(), OWNER_PASSWORD.clone(),
                    "First school?", "A private hint",
                    "North".toCharArray());
        }

        EmailOtpChallenge beginRegistration() {
            return service.beginRegistration("Student User", USER_EMAIL,
                    "2310000", USER_PASSWORD.clone(), USER_PASSWORD.clone(),
                    "First school?", "A private hint",
                    "School".toCharArray());
        }

        void completeRegistration() {
            EmailOtpChallenge challenge = beginRegistration();
            service.verifyRegistration(challenge.challengeIdentifier(),
                    gateway.currentCode);
        }
    }

    private static final class FakeGateway
            implements EmailVerificationGateway {
        private final MutableClock clock;
        private final Map<String, Verification> challenges = new HashMap<>();
        private int sequence;
        String lastEmail = "";
        OtpPurpose lastPurpose;
        String currentCode = "";
        boolean failDelivery;

        FakeGateway(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public EmailOtpChallenge sendCode(
                String email, OtpPurpose purpose, String existing) {
            if (failDelivery) {
                throw new AuthException("Email delivery unavailable.");
            }
            if (existing != null && !existing.isBlank()) {
                Verification previous = challenges.get(existing);
                if (previous == null || !previous.email.equals(email)
                        || previous.purpose != purpose) {
                    throw new AuthException("Challenge mismatch.");
                }
                challenges.remove(existing);
            }
            lastEmail = email;
            lastPurpose = purpose;
            currentCode = "%06d".formatted(++sequence);
            String identifier = "challenge_%020d".formatted(sequence);
            challenges.put(identifier,
                    new Verification(email, purpose, currentCode));
            Instant now = clock.instant();
            return new EmailOtpChallenge(identifier, email, purpose,
                    now.plusSeconds(600), now.plusSeconds(60));
        }

        @Override
        public void verifyCode(
                String email, OtpPurpose purpose, String identifier,
                String code) {
            Verification expected = challenges.get(identifier);
            if (expected == null || !expected.email.equals(email)
                    || expected.purpose != purpose
                    || !expected.code.equals(code)) {
                throw new AuthException("Invalid or expired code.");
            }
            challenges.remove(identifier);
        }

        private record Verification(
                String email, OtpPurpose purpose, String code) {
        }
    }

    private static final class FailingRepository
            implements LocalUserRepository {
        private final LocalUserRepository delegate;
        boolean failWrites;

        FailingRepository(LocalUserRepository delegate) {
            this.delegate = delegate;
        }

        @Override public List<LocalUserRecord> findAll() {
            return delegate.findAll();
        }
        @Override public Optional<LocalUserRecord> findById(String id) {
            return delegate.findById(id);
        }
        @Override public Optional<LocalUserRecord> findByEmail(String email) {
            return delegate.findByEmail(email);
        }
        @Override public Optional<LocalUserRecord> findOwner() {
            return delegate.findOwner();
        }
        @Override public void save(LocalUserRecord record) {
            if (failWrites) {
                throw new IllegalStateException("simulated write failure");
            }
            delegate.save(record);
        }
    }

    private static final class MemoryAuditRepository
            implements AuditRepository {
        final List<AuditEvent> events = new ArrayList<>();

        @Override public void append(AuditEvent event) {
            events.add(event);
        }
        @Override public List<AuditEvent> findAll() {
            return List.copyOf(events);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }
        void advance(Duration duration) {
            now = now.plus(duration);
        }
        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override public Clock withZone(ZoneId zone) {
            return this;
        }
        @Override public Instant instant() {
            return now;
        }
    }
}
