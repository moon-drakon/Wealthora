package com.spendwise.auth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.mindrot.jbcrypt.BCrypt;

public final class PasswordService {

    public static final int MINIMUM_LENGTH = 12;
    private static final int BCRYPT_COST = 12;
    private static final int BCRYPT_MAXIMUM_BYTES = 72;
    private static final Set<String> COMMON = Set.of(
            "password123!", "admin123456!", "administrator1!",
            "welcome1234!", "wealthora123!", "qwerty123456!");

    public String hash(char[] password) {
        char[] protectedPassword = requireStrong(password).clone();
        try {
            return BCrypt.hashpw(
                    new String(protectedPassword), BCrypt.gensalt(BCRYPT_COST));
        } finally {
            Arrays.fill(protectedPassword, '\0');
        }
    }

    public boolean matches(char[] password, String passwordHash) {
        if (password == null || passwordHash == null
                || !passwordHash.startsWith("$2")) {
            return false;
        }
        char[] protectedPassword = password.clone();
        try {
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
                    "Password must contain at least 12 characters.");
        }
        int utf8Bytes = new String(password).getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > BCRYPT_MAXIMUM_BYTES) {
            throw new AuthException(
                    "Password must not exceed 72 UTF-8 bytes.");
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (char character : password) {
            upper |= Character.isUpperCase(character);
            lower |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
            symbol |= !Character.isLetterOrDigit(character);
        }
        if (!(upper && lower && digit && symbol)) {
            throw new AuthException(
                    "Password must include uppercase, lowercase, number, and symbol characters.");
        }
        String normalized = new String(password).toLowerCase(Locale.ROOT);
        if (COMMON.contains(normalized)
                || normalized.contains("admin")
                || normalized.contains("default")) {
            throw new AuthException(
                    "Choose a password that is not common or administrator-themed.");
        }
        return password;
    }
}
