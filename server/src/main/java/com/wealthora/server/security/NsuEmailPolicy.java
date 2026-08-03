package com.wealthora.server.security;

import com.wealthora.server.api.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class NsuEmailPolicy {

    private static final String DOMAIN = "northsouth.edu";

    private NsuEmailPolicy() {
    }

    public static String require(String email) {
        String normalized = email == null ? ""
                : email.strip().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('@');
        if (separator <= 0 || separator != normalized.indexOf('@')
                || !normalized.substring(separator + 1).equals(DOMAIN)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "NSU_EMAIL_REQUIRED",
                    "Use an official @northsouth.edu email address.");
        }
        return normalized;
    }
}
