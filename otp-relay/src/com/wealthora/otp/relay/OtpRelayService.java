package com.wealthora.otp.relay;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class OtpRelayService {

    static final Duration EXPIRY = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAXIMUM_ATTEMPTS = 5;
    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final int EMAIL_REQUEST_LIMIT = 5;
    private static final int ADDRESS_REQUEST_LIMIT = 30;
    private static final Base64.Encoder IDENTIFIER_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private final byte[] signingSecret;
    private final MailDelivery mailDelivery;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Map<String, RequestWindow> emailWindows = new HashMap<>();
    private final Map<String, RequestWindow> addressWindows = new HashMap<>();

    OtpRelayService(byte[] signingSecret, MailDelivery mailDelivery) {
        this(signingSecret, mailDelivery, Clock.systemUTC(),
                new SecureRandom());
    }

    OtpRelayService(
            byte[] signingSecret,
            MailDelivery mailDelivery,
            Clock clock,
            SecureRandom random) {
        Objects.requireNonNull(signingSecret, "Signing secret is required.");
        if (signingSecret.length < 32) {
            throw new IllegalArgumentException(
                    "OTP signing secret must contain at least 32 bytes.");
        }
        this.signingSecret = signingSecret.clone();
        this.mailDelivery = Objects.requireNonNull(mailDelivery);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    synchronized AcceptedChallenge requestCode(
            String emailValue,
            OtpPurpose purpose,
            String existingChallengeIdentifier,
            String remoteAddress) {
        Instant now = clock.instant();
        cleanup(now);
        String email = normalizeInstitutionalEmail(emailValue);
        OtpPurpose requiredPurpose = Objects.requireNonNull(purpose);
        String address = normalizeAddress(remoteAddress);
        String existingId = existingChallengeIdentifier == null
                ? "" : existingChallengeIdentifier.strip();
        Challenge existing = findCurrentChallenge(email, requiredPurpose);
        if (!existingId.isEmpty()) {
            Challenge requested = challenges.get(existingId);
            if (requested == null || requested != existing) {
                throw new InvalidRequestException(
                        "OTP challenge is invalid or expired.");
            }
        }
        if (existing != null) {
            if (now.isBefore(existing.resendAvailableAt())) {
                throw new RateLimitException();
            }
        }
        consumeLimit(emailWindows, email, EMAIL_REQUEST_LIMIT, now);
        consumeLimit(addressWindows, address, ADDRESS_REQUEST_LIMIT, now);

        String challengeId = randomIdentifier();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Challenge replacement = new Challenge(
                challengeId, email, requiredPurpose,
                digest(challengeId, code), now.plus(EXPIRY),
                now.plus(RESEND_COOLDOWN), 0);
        mailDelivery.sendVerificationCode(email, code, requiredPurpose);
        if (existing != null) {
            challenges.remove(existing.identifier());
        }
        challenges.put(challengeId, replacement);
        return new AcceptedChallenge(challengeId,
                EXPIRY.toSeconds(), RESEND_COOLDOWN.toSeconds());
    }

    private Challenge findCurrentChallenge(
            String email, OtpPurpose purpose) {
        return challenges.values().stream()
                .filter(challenge -> challenge.email().equals(email)
                        && challenge.purpose() == purpose)
                .findFirst().orElse(null);
    }

    synchronized boolean verifyCode(
            String emailValue,
            OtpPurpose purpose,
            String challengeIdentifier,
            String codeValue) {
        Instant now = clock.instant();
        cleanup(now);
        String email;
        try {
            email = normalizeInstitutionalEmail(emailValue);
        } catch (InvalidRequestException exception) {
            return false;
        }
        String challengeId = challengeIdentifier == null
                ? "" : challengeIdentifier.strip();
        String code = codeValue == null ? "" : codeValue.strip();
        if (!challengeId.matches("[A-Za-z0-9_-]{20,120}")
                || !code.matches("[0-9]{6}")) {
            return false;
        }
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            return false;
        }
        if (!now.isBefore(challenge.expiresAt())) {
            challenges.remove(challengeId);
            return false;
        }
        if (challenge.purpose() != purpose
                || !challenge.email().equals(email)) {
            return false;
        }
        int attempts = challenge.failedAttempts() + 1;
        boolean verified = MessageDigest.isEqual(
                challenge.codeDigest(), digest(challengeId, code));
        if (verified || attempts >= MAXIMUM_ATTEMPTS) {
            challenges.remove(challengeId);
        } else {
            challenges.put(challengeId, challenge.withFailedAttempts(attempts));
        }
        return verified;
    }

    private void consumeLimit(
            Map<String, RequestWindow> windows,
            String key,
            int maximum,
            Instant now) {
        RequestWindow current = windows.get(key);
        if (current == null || !now.isBefore(current.startedAt().plus(RATE_WINDOW))) {
            windows.put(key, new RequestWindow(now, 1));
            return;
        }
        if (current.count() >= maximum) {
            throw new RateLimitException();
        }
        windows.put(key, new RequestWindow(current.startedAt(),
                current.count() + 1));
    }

    private void cleanup(Instant now) {
        challenges.values().removeIf(
                challenge -> !now.isBefore(challenge.expiresAt()));
        removeExpiredWindows(emailWindows, now);
        removeExpiredWindows(addressWindows, now);
    }

    private static void removeExpiredWindows(
            Map<String, RequestWindow> windows, Instant now) {
        Iterator<RequestWindow> iterator = windows.values().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().startedAt().plus(RATE_WINDOW))) {
                iterator.remove();
            }
        }
    }

    private byte[] digest(String challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return mac.doFinal((challengeId + ":" + code)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.",
                    exception);
        }
    }

    private String randomIdentifier() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return IDENTIFIER_ENCODER.encodeToString(value);
    }

    static String normalizeInstitutionalEmail(String value) {
        String email = value == null ? ""
                : value.strip().toLowerCase(Locale.ROOT);
        int separator = email.indexOf('@');
        if (separator <= 0 || separator != email.lastIndexOf('@')
                || !email.substring(separator + 1).equals("northsouth.edu")
                || email.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidRequestException(
                    "A valid institutional email is required.");
        }
        return email;
    }

    private static String normalizeAddress(String value) {
        String address = value == null ? "" : value.strip();
        return address.isEmpty() ? "unknown" : address;
    }

    record AcceptedChallenge(
            String challengeIdentifier,
            long expiresInSeconds,
            long resendAfterSeconds) {
    }

    private record Challenge(
            String identifier,
            String email,
            OtpPurpose purpose,
            byte[] codeDigest,
            Instant expiresAt,
            Instant resendAvailableAt,
            int failedAttempts) {

        Challenge withFailedAttempts(int value) {
            return new Challenge(identifier, email, purpose, codeDigest,
                    expiresAt, resendAvailableAt, value);
        }
    }

    private record RequestWindow(Instant startedAt, int count) {
    }
}
