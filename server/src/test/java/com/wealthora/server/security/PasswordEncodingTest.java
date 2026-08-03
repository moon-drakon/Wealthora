package com.wealthora.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordEncodingTest {

    private final BackwardCompatibleBcryptPasswordEncoder encoder =
            new BackwardCompatibleBcryptPasswordEncoder(4);

    @Test
    void longPasswordsDoNotUseBcryptTruncation() {
        String first = "a1" + "x".repeat(100);
        String second = first.substring(0, 80) + "different9";
        String encoded = encoder.encode(first);
        assertTrue(encoded.startsWith("{bcrypt-sha256}$2"));
        assertTrue(encoder.matches(first, encoded));
        assertFalse(encoder.matches(second, encoded));
    }

    @Test
    void legacyBcryptHashesRemainUsable() {
        String password = "LegacyOwner1!";
        String legacy = new BCryptPasswordEncoder(4).encode(password);
        assertTrue(encoder.matches(password, legacy));
        assertTrue(encoder.upgradeEncoding(legacy));
    }
}
