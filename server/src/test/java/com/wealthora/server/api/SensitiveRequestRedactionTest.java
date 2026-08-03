package com.wealthora.server.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveRequestRedactionTest {

    private static final String SECRET = "do-not-log-this-secret1";

    @Test
    void authenticationRequestsRedactPasswordsCodesAndTokens() {
        assertRedacted(new RegisterRequest("Student",
                "student@northsouth.edu", null, chars(), chars(), true));
        assertRedacted(new LoginRequest(
                "student@northsouth.edu", chars(), "Test device"));
        assertRedacted(new RefreshRequest(chars()));
        assertRedacted(new ResetPasswordRequest(
                "student@northsouth.edu", chars(), chars(), chars()));
        assertRedacted(new ChangePasswordRequest(chars(), chars(), chars()));
        assertRedacted(new SetPasswordRequest(chars(), chars()));
        assertRedacted(new VerifyEmailRequest(
                "student@northsouth.edu", SECRET));
    }

    private static char[] chars() {
        return SECRET.toCharArray();
    }

    private static void assertRedacted(Object request) {
        String text = request.toString();
        assertTrue(text.contains("[REDACTED]"));
        assertFalse(text.contains(SECRET));
    }
}
