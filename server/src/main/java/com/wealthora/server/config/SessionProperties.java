package com.wealthora.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.session")
public record SessionProperties(
        Duration accessExpiry,
        Duration refreshExpiry,
        Duration lockDuration,
        int maximumFailedAttempts) {

    public SessionProperties {
        accessExpiry = accessExpiry == null
                ? Duration.ofMinutes(15) : accessExpiry;
        refreshExpiry = refreshExpiry == null
                ? Duration.ofDays(30) : refreshExpiry;
        lockDuration = lockDuration == null
                ? Duration.ofMinutes(15) : lockDuration;
        if (maximumFailedAttempts < 1) {
            maximumFailedAttempts = 5;
        }
    }
}
