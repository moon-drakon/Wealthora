package com.spendwise.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.mindrot.jbcrypt.BCrypt;

public final class PasswordService {

    public static final int MINIMUM_LENGTH = 8;
    public static final int MAXIMUM_LENGTH = 128;
    private static final int BCRYPT_COST = 12;
    private static final String BCRYPT_SHA256_PREFIX = "{bcrypt-sha256}";

    public String hash(char[] password) {
        char[] protectedPassword = requireStrong(password).clone();
        try {
            return BCRYPT_SHA256_PREFIX + BCrypt.hashpw(
                    sha256(protectedPassword), BCrypt.gensalt(BCRYPT_COST));
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    public boolean matches(char[] password, String passwordHash) {
        if (password == null || passwordHash == null
                || (!passwordHash.startsWith("$2")
                && !passwordHash.startsWith(BCRYPT_SHA256_PREFIX))) {
            return false;
        }
        char[] protectedPassword = password.clone();
        try {
            if (passwordHash.startsWith(BCRYPT_SHA256_PREFIX)) {
                return BCrypt.checkpw(sha256(protectedPassword),
                        passwordHash.substring(BCRYPT_SHA256_PREFIX.length()));
            }
            return BCrypt.checkpw(new String(protectedPassword), passwordHash);
        } catch (RuntimeException exception) {
            return false;
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    public char[] requireStrong(char[] password) {
        if (password == null || password.length < MINIMUM_LENGTH) {
            throw new AuthException(
                    "Password must contain at least 8 characters.");
        }
        if (password.length > MAXIMUM_LENGTH) {
            throw new AuthException(
                    "Password must not exceed 128 characters.");
        }
        if (Character.isWhitespace(password[0])
                || Character.isWhitespace(password[password.length - 1])) {
            throw new AuthException(
                    "Password must not begin or end with a space.");
        }
        boolean englishLetter = false;
        boolean digit = false;
        for (char character : password) {
            englishLetter |= (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z');
            digit |= character >= '0' && character <= '9';
        }
        if (!englishLetter || !digit) {
            throw new AuthException(
                    "Password must include at least one English letter and one number.");
        }
        return password;
    }

    private static String sha256(char[] password) {
        byte[] bytes = new String(password).getBytes(StandardCharsets.UTF_8);
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
