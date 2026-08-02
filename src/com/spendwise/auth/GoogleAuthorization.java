package com.spendwise.auth;

import java.util.Arrays;

/** Short-lived browser authorization result; callers must close it after exchange. */
public final class GoogleAuthorization implements AutoCloseable {

    private final char[] authorizationCode;
    private final String redirectUri;

    public GoogleAuthorization(char[] authorizationCode, String redirectUri) {
        if (authorizationCode == null || authorizationCode.length == 0) {
            throw new AuthException("Google authorization code is required.");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new AuthException("Google redirect URI is required.");
        }
        this.authorizationCode = authorizationCode.clone();
        this.redirectUri = redirectUri.strip();
    }

    public char[] copyAuthorizationCode() {
        return authorizationCode.clone();
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    @Override
    public void close() {
        Arrays.fill(authorizationCode, '\0');
    }
}
