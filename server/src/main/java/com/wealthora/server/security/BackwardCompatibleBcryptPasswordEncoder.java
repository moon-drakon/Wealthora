package com.wealthora.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Preserves legacy BCrypt hashes while avoiding BCrypt's 72-byte limit. */
public final class BackwardCompatibleBcryptPasswordEncoder
        implements PasswordEncoder {

    private static final String PREFIX = "{bcrypt-sha256}";
    private final BCryptPasswordEncoder bcrypt;

    public BackwardCompatibleBcryptPasswordEncoder(int strength) {
        bcrypt = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + bcrypt.encode(sha256(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false;
        try {
            if (encodedPassword.startsWith(PREFIX)) {
                return bcrypt.matches(sha256(rawPassword),
                        encodedPassword.substring(PREFIX.length()));
            }
            return bcrypt.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith(PREFIX);
    }

    private static String sha256(CharSequence value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
