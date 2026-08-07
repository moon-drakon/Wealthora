package com.wealthora.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.registration")
public record RegistrationProperties(
        boolean requiresAdminApproval,
        boolean emailVerificationRequired,
        Duration verificationExpiry,
        Duration resendCooldown,
        int maximumVerificationAttempts) {

    public RegistrationProperties {
        verificationExpiry = verificationExpiry == null
                ? Duration.ofMinutes(10) : verificationExpiry;
        resendCooldown = resendCooldown == null
                ? Duration.ofMinutes(1) : resendCooldown;
        if (maximumVerificationAttempts < 1) {
            maximumVerificationAttempts = 5;
        }
    }
}
