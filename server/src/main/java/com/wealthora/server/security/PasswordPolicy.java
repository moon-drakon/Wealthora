package com.wealthora.server.security;

import com.wealthora.server.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class PasswordPolicy {

    public void requireStrong(char[] password) {
        if (password == null || password.length < 8) {
            reject("Password must contain at least 8 characters.");
        }
        if (password.length > 128) {
            reject("Password must not exceed 128 characters.");
        }
        if (Character.isWhitespace(password[0])
                || Character.isWhitespace(password[password.length - 1])) {
            reject("Password must not begin or end with a space.");
        }
        boolean englishLetter = false;
        boolean digit = false;
        for (char character : password) {
            englishLetter |= (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z');
            digit |= character >= '0' && character <= '9';
        }
        if (!englishLetter || !digit) {
            reject("Password must include at least one English letter and one number.");
        }
    }

    private static void reject(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "WEAK_PASSWORD", message);
    }
}
