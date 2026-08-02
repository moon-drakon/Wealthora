package com.spendwise.auth.local;

import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.Objects;

public record LocalUserRecord(
        AuthenticatedUser user,
        String passwordHash,
        int failedLoginAttempts,
        Instant lockedUntil) {

    public LocalUserRecord {
        Objects.requireNonNull(user, "User is required.");
        if (passwordHash == null || !passwordHash.startsWith("$2")) {
            throw new AuthException("A valid BCrypt password hash is required.");
        }
        if (failedLoginAttempts < 0) {
            throw new AuthException("Failed-login count cannot be negative.");
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
                value, passwordHash, attempts, lockExpiration);
    }
}
