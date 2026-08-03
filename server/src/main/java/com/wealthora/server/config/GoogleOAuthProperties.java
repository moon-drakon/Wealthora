package com.wealthora.server.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.google-oauth")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        Duration flowExpiry) {

    public GoogleOAuthProperties {
        clientId = clean(clientId);
        clientSecret = clean(clientSecret);
        redirectUri = clean(redirectUri);
        flowExpiry = flowExpiry == null || flowExpiry.isNegative()
                || flowExpiry.isZero() ? Duration.ofMinutes(3) : flowExpiry;
    }

    public String configurationProblem() {
        if (clientId.isEmpty()) return "GOOGLE_OAUTH_CLIENT_ID is not configured.";
        if (clientSecret.isEmpty()) return "GOOGLE_OAUTH_CLIENT_SECRET is not configured.";
        if (redirectUri.isEmpty()) return "GOOGLE_OAUTH_REDIRECT_URI is not configured.";
        try {
            URI uri = URI.create(redirectUri);
            boolean loopback = "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost())
                    || "localhost".equalsIgnoreCase(uri.getHost()));
            if (!("https".equalsIgnoreCase(uri.getScheme()) || loopback)
                    || !"/api/auth/google/callback".equals(uri.getPath())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                return "GOOGLE_OAUTH_REDIRECT_URI must be an HTTPS callback URL ending in /api/auth/google/callback (HTTP is allowed only for loopback development).";
            }
        } catch (IllegalArgumentException exception) {
            return "GOOGLE_OAUTH_REDIRECT_URI is invalid.";
        }
        return "";
    }

    public boolean isConfigured() {
        return configurationProblem().isEmpty();
    }

    @Override
    public String toString() {
        return "GoogleOAuthProperties[clientId=" + clientId
                + ", clientSecret=[REDACTED], redirectUri=" + redirectUri
                + ", flowExpiry=" + flowExpiry + "]";
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
