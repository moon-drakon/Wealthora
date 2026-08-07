package com.spendwise.auth.local;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.Objects;

public record LocalUserRecord(
        AuthenticatedUser user,
        String passwordHash,
        int failedLoginAttempts,
        Instant lockedUntil,
        String recoveryQuestion,
        String recoveryHint,
        String recoveryAnswerHash) {

    public LocalUserRecord(
            AuthenticatedUser user,
            String passwordHash,
            int failedLoginAttempts,
            Instant lockedUntil) {
        this(user, passwordHash, failedLoginAttempts, lockedUntil,
                "", "", "");
    }

    public LocalUserRecord {
        Objects.requireNonNull(user, "User is required.");
        if (passwordHash == null || (!passwordHash.startsWith("$2")
                && !passwordHash.startsWith("{bcrypt-sha256}$2"))) {
            throw new AuthException("A valid BCrypt password hash is required.");
        }
        if (failedLoginAttempts < 0) {
            throw new AuthException("Failed-login count cannot be negative.");
        }
        recoveryQuestion = optional(recoveryQuestion);
        recoveryHint = optional(recoveryHint);
        recoveryAnswerHash = optional(recoveryAnswerHash);
        boolean completeRecovery = !recoveryQuestion.isEmpty()
                && !recoveryAnswerHash.isEmpty();
        if ((!recoveryQuestion.isEmpty() || !recoveryHint.isEmpty()
                || !recoveryAnswerHash.isEmpty()) && !completeRecovery) {
            throw new AuthException(
                    "Recovery question and protected answer must be complete.");
        }
        if (!recoveryAnswerHash.isEmpty()
                && !recoveryAnswerHash.startsWith("$2")
                && !recoveryAnswerHash.startsWith("{bcrypt-sha256}$2")) {
            throw new AuthException(
                    "A valid BCrypt recovery-answer hash is required.");
        }
    }

    public boolean isLockedAt(Instant instant) {
        return lockedUntil != null && lockedUntil.isAfter(instant);
    }

    public LocalUserRecord withAuthenticationState(
            AuthenticatedUser value,
            int attempts,
            Instant lockExpiration) {
        return new LocalUserRecord(
                value, passwordHash, attempts, lockExpiration,
                recoveryQuestion, recoveryHint, recoveryAnswerHash);
    }

    public LocalUserRecord withPasswordHash(String value) {
        return new LocalUserRecord(user, value, 0, null,
                recoveryQuestion, recoveryHint, recoveryAnswerHash);
    }

    public LocalUserRecord withPasswordRecovery(
            String question, String hint, String answerHash) {
        return new LocalUserRecord(user, passwordHash,
                failedLoginAttempts, lockedUntil,
                question, hint, answerHash);
    }

    public boolean hasPasswordRecovery() {
        return !recoveryQuestion.isEmpty() && !recoveryAnswerHash.isEmpty();
    }

    private static String optional(String value) {
        return value == null ? "" : value.strip();
    }
}
