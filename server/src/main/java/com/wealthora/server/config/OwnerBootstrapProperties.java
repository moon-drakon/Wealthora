package com.wealthora.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.owner-bootstrap")
public record OwnerBootstrapProperties(
        String fullName,
        String email,
        String password) {

    public boolean hasAnyValue() {
        return present(fullName) || present(email) || present(password);
    }

    public boolean isComplete() {
        return present(fullName) && present(email) && present(password);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
