package com.spendwise.auth;

import java.util.Locale;

public final class NsuEmailPolicy {

    public static final String DOMAIN = "northsouth.edu";

    private NsuEmailPolicy() {
    }

    public static String requireInstitutionalEmail(String email) {
        String normalized = email == null
                ? "" : email.strip().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('@');
        if (separator <= 0
                || !normalized.substring(separator + 1).equals(DOMAIN)) {
            throw new AuthException(
                    "Use a verified @northsouth.edu email address. "
                    + "Personal Gmail accounts are not accepted.");
        }
        return normalized;
    }
}
