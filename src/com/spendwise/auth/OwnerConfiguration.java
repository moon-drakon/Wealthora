package com.spendwise.auth;

import java.util.Map;
import java.util.Objects;

public final class OwnerConfiguration {

    public static final String ENVIRONMENT_NAME = "APP_OWNER_EMAIL";

    private final String configuredEmail;

    public OwnerConfiguration(String configuredEmail) {
        this.configuredEmail = configuredEmail == null
                ? "" : configuredEmail.strip();
    }

    public static OwnerConfiguration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static OwnerConfiguration fromEnvironment(Map<String, String> environment) {
        return new OwnerConfiguration(Objects.requireNonNull(environment)
                .get(ENVIRONMENT_NAME));
    }

    public boolean isConfigured() {
        return !configuredEmail.isBlank();
    }

    public String getConfiguredEmail() {
        return configuredEmail;
    }

    public String requireOwnerEmail() {
        if (!isConfigured()) {
            throw new AuthConfigurationException(
                    "APP_OWNER_EMAIL is required before the first OWNER can be created.");
        }
        return NsuEmailPolicy.requireInstitutionalEmail(configuredEmail);
    }
}
