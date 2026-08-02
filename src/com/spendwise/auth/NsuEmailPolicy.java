package com.spendwise.auth;

public final class NsuEmailPolicy {

    public static final String DOMAIN = "northsouth.edu";

    private NsuEmailPolicy() {
    }

    public static String requireInstitutionalEmail(String email) {
        String normalized = EmailAddressPolicy.normalize(email);
        if (!EmailAddressPolicy.domainOf(normalized).equals(DOMAIN)) {
            throw new AuthException(
                    "Email and password access requires an official "
                    + "@northsouth.edu account.");
        }
        return normalized;
    }

    public static boolean isInstitutionalEmail(String email) {
        try {
            return EmailAddressPolicy.domainOf(email).equals(DOMAIN);
        } catch (AuthException exception) {
            return false;
        }
    }
}
