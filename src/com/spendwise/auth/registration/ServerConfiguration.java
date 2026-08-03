package com.spendwise.auth.registration;

import com.spendwise.auth.AuthConfigurationException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

public final class ServerConfiguration {

    public static final String ENVIRONMENT_NAME = "WEALTHORA_SERVER_URL";
    private final String configuredUrl;

    public ServerConfiguration(String configuredUrl) {
        this.configuredUrl = configuredUrl == null ? "" : configuredUrl.strip();
    }

    public static ServerConfiguration fromEnvironment() {
        return new ServerConfiguration(System.getenv(ENVIRONMENT_NAME));
    }

    static ServerConfiguration fromEnvironment(Map<String, String> values) {
        return new ServerConfiguration(Objects.requireNonNull(values)
                .get(ENVIRONMENT_NAME));
    }

    public boolean isConfigured() {
        return !configuredUrl.isBlank();
    }

    public URI requireBaseUri() {
        if (!isConfigured()) {
            throw new AuthConfigurationException(
                    "WEALTHORA_SERVER_URL is not configured.");
        }
        URI uri;
        try {
            uri = URI.create(configuredUrl);
        } catch (IllegalArgumentException exception) {
            throw new AuthConfigurationException(
                    "WEALTHORA_SERVER_URL is invalid.", exception);
        }
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!("https".equalsIgnoreCase(uri.getScheme())
                || (loopback && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new AuthConfigurationException(
                    "WEALTHORA_SERVER_URL must use HTTPS; HTTP is allowed only for localhost development.");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized);
    }
}
