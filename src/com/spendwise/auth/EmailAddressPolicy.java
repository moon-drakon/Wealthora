package com.spendwise.auth;

import java.util.Locale;

public final class EmailAddressPolicy {

    private EmailAddressPolicy() {
    }

    public static String normalize(String email) {
        String normalized = email == null
                ? "" : email.strip().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('@');
        if (separator <= 0
                || separator != normalized.lastIndexOf('@')
                || separator == normalized.length() - 1
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new AuthException("Enter a valid email address.");
        }
        String domain = normalized.substring(separator + 1);
        if (domain.startsWith(".") || domain.endsWith(".")
                || domain.contains("..")) {
            throw new AuthException("Enter a valid email address.");
        }
        return normalized;
    }

    public static String domainOf(String email) {
        String normalized = normalize(email);
        return normalized.substring(normalized.indexOf('@') + 1);
    }
}
