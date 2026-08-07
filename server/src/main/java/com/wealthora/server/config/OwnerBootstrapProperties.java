package com.wealthora.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.owner-bootstrap")
public record OwnerBootstrapProperties(
        String fullName,
        String email,
        String password,
        String claimToken) {

    public boolean isCreationComplete() {
        return present(fullName) && present(email) && present(password);
    }

    public boolean isClaimComplete() {
        return present(email) && present(claimToken)
                && claimToken.length() >= 43;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
