package com.wealthora.server.security;

import com.wealthora.server.api.ApiException;
import java.nio.CharBuffer;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class PasswordPolicy {

    private static final Set<String> COMMON = Set.of(
            "password123!", "admin123456!", "administrator1!",
            "welcome1234!", "wealthora123!", "qwerty123456!");

    public void requireStrong(char[] password) {
        if (password == null || password.length < 12) {
            reject("Password must contain at least 12 characters.");
        }
        if (password.length > 72) {
            reject("Password must not exceed 72 characters.");
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
            reject("Password must include uppercase, lowercase, number, and symbol characters.");
        }
        String normalized = CharBuffer.wrap(password).toString()
                .toLowerCase(Locale.ROOT);
        if (COMMON.contains(normalized)
                || normalized.contains("admin")
                || normalized.contains("default")) {
            reject("Choose a password that is not common or administrator-themed.");
        }
    }

    private static void reject(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "WEAK_PASSWORD", message);
    }
}
