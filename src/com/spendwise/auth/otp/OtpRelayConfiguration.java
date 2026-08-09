package com.spendwise.auth.otp;

import com.spendwise.auth.AuthConfigurationException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

public final class OtpRelayConfiguration {

    public static final String ENVIRONMENT_NAME = "WEALTHORA_OTP_RELAY_URL";
    private final String configuredUrl;

    public OtpRelayConfiguration(String configuredUrl) {
        this.configuredUrl = configuredUrl == null ? "" : configuredUrl.strip();
    }

    public static OtpRelayConfiguration fromEnvironment() {
        return new OtpRelayConfiguration(System.getenv(ENVIRONMENT_NAME));
    }

    static OtpRelayConfiguration fromEnvironment(Map<String, String> values) {
        return new OtpRelayConfiguration(Objects.requireNonNull(values)
                .get(ENVIRONMENT_NAME));
    }

    public boolean isConfigured() {
        return !configuredUrl.isBlank();
    }

    public URI requireBaseUri() {
        if (!isConfigured()) {
            throw new AuthConfigurationException(
                    "WEALTHORA_OTP_RELAY_URL is not configured.");
        }
        URI uri;
        try {
            uri = URI.create(configuredUrl);
        } catch (IllegalArgumentException exception) {
            throw new AuthConfigurationException(
                    "WEALTHORA_OTP_RELAY_URL is invalid.", exception);
        }
        String host = uri.getHost();
        if (!uri.isAbsolute() || host == null || host.isBlank()
                || (uri.getPath() != null && !uri.getPath().isBlank()
                        && !uri.getPath().equals("/"))) {
            throw new AuthConfigurationException(
                    "The OTP relay URL must be an absolute origin without a path.");
        }
        boolean loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!("https".equalsIgnoreCase(uri.getScheme())
                || (loopback && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new AuthConfigurationException(
                    "The OTP relay must use HTTPS; HTTP is allowed only for local development.");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new AuthConfigurationException(
                    "The OTP relay URL must not contain credentials, a query, or a fragment.");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized);
    }
}
