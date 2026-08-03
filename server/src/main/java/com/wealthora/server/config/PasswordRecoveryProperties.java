package com.wealthora.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.password-recovery")
public record PasswordRecoveryProperties(
        Duration resetExpiry, Duration requestCooldown,
        int maximumResetAttempts) {

    public PasswordRecoveryProperties {
        resetExpiry = resetExpiry == null
                ? Duration.ofMinutes(15) : resetExpiry;
        requestCooldown = requestCooldown == null
                ? Duration.ofMinutes(1) : requestCooldown;
        if (maximumResetAttempts < 1) maximumResetAttempts = 5;
    }
}
