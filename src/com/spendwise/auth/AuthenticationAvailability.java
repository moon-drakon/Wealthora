package com.spendwise.auth;

public record AuthenticationAvailability(
        String serverStatus,
        boolean emailProviderAvailable,
        boolean googleOAuthAvailable) {

    public static AuthenticationAvailability serverUrlMissing() {
        return new AuthenticationAvailability(
                "Server URL missing", false, false);
    }

    public static AuthenticationAvailability serverUnavailable() {
        return new AuthenticationAvailability(
                "Server unavailable", false, false);
    }

    public static AuthenticationAvailability connected(
            boolean emailProviderAvailable,
            boolean googleOAuthAvailable) {
        return new AuthenticationAvailability(
                "Connected", emailProviderAvailable, googleOAuthAvailable);
    }

    public String emailStatus() {
        return emailProviderAvailable
                ? "Email provider configured"
                : "Email provider unavailable";
    }

    public String googleStatus() {
        return googleOAuthAvailable
                ? "Google OAuth configured"
                : "Google OAuth unavailable";
    }
}
