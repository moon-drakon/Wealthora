package com.wealthora.otp.relay;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

public final class OtpRelayServiceTest {

    private OtpRelayServiceTest() {
    }

    public static void main(String[] args) {
        requestVerifyAndSingleUse();
        bindingMismatchDoesNotInvalidateChallenge();
        failedAttemptLimit();
        resendInvalidatesPreviousCode();
        freshRequestCannotBypassNewestRule();
        failedDeliveryPreservesExistingChallenge();
        passwordResetPurposeSurvivesResend();
        expiryAndRateLimits();
        addressRateLimit();
        configurationPolicy();
        strictJsonParsing();
        emailTemplatesAndMultipartMime();
        System.out.println("OtpRelayServiceTest passed");
    }

    private static void bindingMismatchDoesNotInvalidateChallenge() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge challenge = fixture.request("");
        String code = fixture.mail.code;
        check(!fixture.service.verifyCode("other@northsouth.edu",
                OtpPurpose.REGISTRATION,
                challenge.challengeIdentifier(), code), "wrong email");
        check(!fixture.service.verifyCode("student@northsouth.edu",
                OtpPurpose.PASSWORD_RESET,
                challenge.challengeIdentifier(), code), "wrong purpose");
        check(fixture.verify(challenge, code),
                "binding mismatch must not invalidate the valid challenge");
    }

    private static void requestVerifyAndSingleUse() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge challenge = fixture.request("");
        check(challenge.challengeIdentifier()
                .matches("[A-Za-z0-9_-]{20,120}"), "challenge id");
        check(challenge.expiresInSeconds() == 600, "expiry");
        check(challenge.resendAfterSeconds() == 60, "resend cooldown");
        check(fixture.verify(challenge, fixture.mail.code), "correct code");
        check(!fixture.verify(challenge, fixture.mail.code), "single use");
    }

    private static void failedAttemptLimit() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge challenge = fixture.request("");
        String wrongCode = fixture.mail.code.equals("000000")
                ? "999999" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            check(!fixture.verify(challenge, wrongCode), "wrong code");
        }
        check(!fixture.verify(challenge, fixture.mail.code),
                "challenge invalidated after five failures");
    }

    private static void resendInvalidatesPreviousCode() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge first = fixture.request("");
        String firstCode = fixture.mail.code;
        expect(RateLimitException.class,
                () -> fixture.request(first.challengeIdentifier()));
        fixture.clock.advance(Duration.ofSeconds(60));
        OtpRelayService.AcceptedChallenge second = fixture.request(
                first.challengeIdentifier());
        String secondCode = fixture.mail.code;
        check(!fixture.verify(first, firstCode), "old challenge invalidated");
        check(fixture.verify(second, secondCode), "replacement challenge");
    }

    private static void failedDeliveryPreservesExistingChallenge() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge first = fixture.request("");
        String firstCode = fixture.mail.code;
        fixture.clock.advance(Duration.ofSeconds(60));
        fixture.mail.fail = true;
        expect(IllegalStateException.class,
                () -> fixture.request(first.challengeIdentifier()));
        fixture.mail.fail = false;
        check(fixture.verify(first, firstCode),
                "delivery failure must not remove prior challenge");
    }

    private static void passwordResetPurposeSurvivesResend() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge first =
                fixture.service.requestCode("student@northsouth.edu",
                        OtpPurpose.PASSWORD_RESET, "", "192.0.2.10");
        String firstCode = fixture.mail.code;
        check(fixture.mail.purpose == OtpPurpose.PASSWORD_RESET,
                "password reset template purpose");
        fixture.clock.advance(Duration.ofSeconds(60));
        OtpRelayService.AcceptedChallenge second =
                fixture.service.requestCode("student@northsouth.edu",
                        OtpPurpose.PASSWORD_RESET,
                        first.challengeIdentifier(), "192.0.2.10");
        String secondCode = fixture.mail.code;
        check(fixture.mail.purpose == OtpPurpose.PASSWORD_RESET,
                "password reset resend template purpose");
        check(!fixture.service.verifyCode("student@northsouth.edu",
                        OtpPurpose.PASSWORD_RESET,
                        first.challengeIdentifier(), firstCode),
                "password reset resend invalidates older code");
        check(fixture.service.verifyCode("student@northsouth.edu",
                        OtpPurpose.PASSWORD_RESET,
                        second.challengeIdentifier(), secondCode),
                "password reset replacement code");
    }

    private static void freshRequestCannotBypassNewestRule() {
        Fixture fixture = new Fixture();
        OtpRelayService.AcceptedChallenge first = fixture.request("");
        String firstCode = fixture.mail.code;
        expect(RateLimitException.class, () -> fixture.request(""));
        fixture.clock.advance(Duration.ofSeconds(60));
        OtpRelayService.AcceptedChallenge second = fixture.request("");
        String secondCode = fixture.mail.code;
        check(!fixture.verify(first, firstCode),
                "fresh request invalidates older challenge");
        check(fixture.verify(second, secondCode),
                "newest fresh request remains valid");
    }

    private static void expiryAndRateLimits() {
        Fixture expiry = new Fixture();
        OtpRelayService.AcceptedChallenge challenge = expiry.request("");
        String code = expiry.mail.code;
        expiry.clock.advance(Duration.ofSeconds(600));
        check(!expiry.verify(challenge, code), "expired challenge");

        Fixture limit = new Fixture();
        for (int request = 0; request < 5; request++) {
            limit.service.requestCode("limit@northsouth.edu",
                    OtpPurpose.REGISTRATION, "", "192.0.2.1");
            limit.clock.advance(Duration.ofSeconds(60));
        }
        expect(RateLimitException.class,
                () -> limit.service.requestCode("limit@northsouth.edu",
                        OtpPurpose.REGISTRATION, "", "192.0.2.1"));
    }

    private static void strictJsonParsing() {
        Map<String, Object> values = JsonSupport.parseObject(
                "{\"email\":\"student@northsouth.edu\","
                        + "\"purpose\":\"REGISTRATION\","
                        + "\"challengeId\":\"\"}");
        JsonSupport.requireExactKeys(values,
                "email", "purpose", "challengeId");
        expect(InvalidRequestException.class,
                () -> JsonSupport.parseObject("{\"email\":false}"));
        expect(InvalidRequestException.class,
                () -> JsonSupport.requireExactKeys(values, "email"));
    }

    private static void emailTemplatesAndMultipartMime() {
        String boundary = "wealthora_test_boundary";
        String registration = OtpEmailTemplate.multipartMessage(
                "relay@example.test", "Wealthora Security",
                "student@northsouth.edu", "135790",
                OtpPurpose.REGISTRATION, boundary);
        check(registration.startsWith(
                "From: Wealthora Security <relay@example.test>\r\n"),
                "branded from header");
        check(registration.contains(
                "Subject: Verify your Wealthora email\r\n"),
                "registration subject");
        check(registration.contains(
                "Content-Type: multipart/alternative;\r\n"),
                "multipart alternative header");
        check(registration.contains(
                "Content-Type: text/plain; charset=UTF-8\r\n"),
                "plain text MIME part");
        check(registration.contains(
                "Content-Type: text/html; charset=UTF-8\r\n"),
                "HTML MIME part");
        check(count(registration,
                "Content-Transfer-Encoding: base64\r\n") == 2,
                "both MIME parts use UTF-8-safe transfer encoding");
        check(!registration.contains("135790"),
                "OTP is absent from headers and raw MIME transport");

        String registrationPlain = decodePart(
                registration, boundary, "text/plain");
        String registrationHtml = decodePart(
                registration, boundary, "text/html");
        check(registrationPlain.contains("Wealthora Email Verification"),
                "registration plain heading");
        check(registrationPlain.contains("Verification code: 135790"),
                "registration plain OTP");
        check(registrationPlain.contains("expires in 10 minutes"),
                "plain expiry notice");
        check(registrationPlain.contains("Do not share this code"),
                "plain sharing warning");
        check(registrationPlain.contains("ignore this email"),
                "plain ignore notice");
        check(registrationHtml.contains("Verify your email address"),
                "registration HTML heading");
        check(registrationHtml.contains(">135790</div>"),
                "registration HTML OTP");
        check(registrationHtml.contains("role=\"presentation\""),
                "email-compatible table layout");
        check(registrationHtml.contains("style=\""),
                "inline email styling");
        check(registrationHtml.contains(
                "name=\"viewport\" content=\"width=device-width,"),
                "responsive viewport");
        check(registrationHtml.contains(
                "width:100%;max-width:600px"),
                "responsive fluid container");
        check(registrationHtml.contains("expires in 10 minutes"),
                "HTML expiry notice");
        check(registrationHtml.contains("Do not share this code"),
                "HTML sharing warning");
        check(registrationHtml.contains("ignore this email"),
                "HTML ignore notice");
        check(!registrationHtml.toLowerCase(java.util.Locale.ROOT)
                .matches("(?s).*(<script|<img|https?://|@font-face).*"),
                "HTML contains no scripts or external resources");

        String reset = OtpEmailTemplate.multipartMessage(
                "relay@example.test", "Wealthora Security",
                "student@northsouth.edu", "246802",
                OtpPurpose.PASSWORD_RESET, boundary);
        check(reset.contains(
                "Subject: Reset your Wealthora password\r\n"),
                "password reset subject");
        check(!reset.contains("246802"),
                "password reset OTP is absent from raw MIME headers");
        check(decodePart(reset, boundary, "text/plain")
                .contains("Wealthora Password Reset"),
                "password reset plain heading");
        check(decodePart(reset, boundary, "text/html")
                .contains("Reset your password"),
                "password reset HTML heading");

        check(OtpEmailTemplate.escapeHtml("<&>\"'")
                .equals("&lt;&amp;&gt;&quot;&#39;"),
                "HTML escaping");
        expect(IllegalArgumentException.class,
                () -> OtpEmailTemplate.multipartMessage(
                        "relay@example.test",
                        "Wealthora Security",
                        "student@northsouth.edu\r\nBcc: other@example.test",
                        "135790", OtpPurpose.REGISTRATION, boundary));
        expect(IllegalArgumentException.class,
                () -> OtpEmailTemplate.multipartMessage(
                        "relay@example.test", "Wealthora\r\nBcc",
                        "student@northsouth.edu", "135790",
                        OtpPurpose.REGISTRATION, boundary));
    }

    private static String decodePart(
            String message, String boundary, String contentType) {
        String marker = "Content-Type: " + contentType
                + "; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n";
        int start = message.indexOf(marker);
        check(start >= 0, contentType + " marker");
        start += marker.length();
        int end = message.indexOf("\r\n--" + boundary, start);
        check(end > start, contentType + " boundary");
        return new String(Base64.getMimeDecoder().decode(
                message.substring(start, end)), StandardCharsets.UTF_8);
    }

    private static int count(String text, String value) {
        int result = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            result++;
            offset += value.length();
        }
        return result;
    }

    private static void addressRateLimit() {
        Fixture fixture = new Fixture();
        for (int request = 0; request < 30; request++) {
            fixture.service.requestCode("student" + request
                            + "@northsouth.edu",
                    OtpPurpose.REGISTRATION, "", "198.51.100.20");
        }
        expect(RateLimitException.class,
                () -> fixture.service.requestCode(
                        "student30@northsouth.edu",
                        OtpPurpose.REGISTRATION, "", "198.51.100.20"));
    }

    private static void configurationPolicy() {
        Map<String, String> local = new HashMap<>();
        local.put("WEALTHORA_RELAY_BIND_ADDRESS", "127.0.0.1");
        local.put("WEALTHORA_RELAY_ALLOW_HTTP_LOOPBACK", "true");
        local.put("WEALTHORA_OTP_SIGNING_SECRET",
                "0123456789abcdef0123456789abcdef");
        local.put("WEALTHORA_SMTP_HOST", "smtp.example.test");
        local.put("WEALTHORA_SMTP_USERNAME", "relay@example.test");
        local.put("WEALTHORA_SMTP_PASSWORD", "placeholder-password");
        RelayConfiguration configuration =
                RelayConfiguration.fromEnvironment(local);
        check(configuration.loopbackHttp(), "loopback HTTP opt-in");
        check(configuration.bindAddress().isLoopbackAddress(),
                "loopback bind");
        check(configuration.senderName().equals("Wealthora Security"),
                "default sender name");

        Map<String, String> customSenderName = new HashMap<>(local);
        customSenderName.put("WEALTHORA_SMTP_FROM_NAME", "Wealthora Accounts");
        check(RelayConfiguration.fromEnvironment(customSenderName)
                .senderName().equals("Wealthora Accounts"),
                "custom sender name");

        Map<String, String> publicHttp = new HashMap<>(local);
        publicHttp.put("WEALTHORA_RELAY_BIND_ADDRESS", "0.0.0.0");
        expect(IllegalArgumentException.class,
                () -> RelayConfiguration.fromEnvironment(publicHttp));

        Map<String, String> production = new HashMap<>(local);
        production.remove("WEALTHORA_RELAY_ALLOW_HTTP_LOOPBACK");
        expect(IllegalArgumentException.class,
                () -> RelayConfiguration.fromEnvironment(production));

        Map<String, String> weakSecret = new HashMap<>(local);
        weakSecret.put("WEALTHORA_OTP_SIGNING_SECRET", "too-short");
        expect(IllegalArgumentException.class,
                () -> RelayConfiguration.fromEnvironment(weakSecret));

        Map<String, String> injectedSender = new HashMap<>(local);
        injectedSender.put("WEALTHORA_SMTP_FROM",
                "relay@example.test\r\nBcc: other@example.test");
        expect(IllegalArgumentException.class,
                () -> RelayConfiguration.fromEnvironment(injectedSender));

        Map<String, String> injectedSenderName = new HashMap<>(local);
        injectedSenderName.put("WEALTHORA_SMTP_FROM_NAME",
                "Wealthora\r\nBcc: other@example.test");
        expect(IllegalArgumentException.class,
                () -> RelayConfiguration.fromEnvironment(injectedSenderName));
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

    private static final class Fixture {
        final MutableClock clock = new MutableClock(
                Instant.parse("2026-08-08T00:00:00Z"));
        final RecordingMail mail = new RecordingMail();
        final OtpRelayService service = new OtpRelayService(
                "0123456789abcdef0123456789abcdef".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8),
                mail, clock, new SecureRandom());

        OtpRelayService.AcceptedChallenge request(String existing) {
            return service.requestCode("student@northsouth.edu",
                    OtpPurpose.REGISTRATION, existing, "192.0.2.10");
        }

        boolean verify(
                OtpRelayService.AcceptedChallenge challenge, String code) {
            return service.verifyCode("student@northsouth.edu",
                    OtpPurpose.REGISTRATION,
                    challenge.challengeIdentifier(), code);
        }
    }

    private static final class RecordingMail implements MailDelivery {
        String code;
        OtpPurpose purpose;
        boolean fail;

        @Override
        public void sendVerificationCode(
                String recipient, String value, OtpPurpose purpose) {
            if (fail) {
                throw new IllegalStateException("mail unavailable");
            }
            check(recipient.endsWith("@northsouth.edu"), "recipient");
            check(value.matches("[0-9]{6}"), "six digit code");
            code = value;
            this.purpose = purpose;
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

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
