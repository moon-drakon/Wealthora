package com.spendwise.auth;

public record GoogleOAuthStatus(
        boolean configured, String message, String redirectUri) {

    public GoogleOAuthStatus {
        message = message == null || message.isBlank()
                ? "Google Sign-In is unavailable." : message.strip();
        redirectUri = redirectUri == null ? "" : redirectUri.strip();
    }
}
