package com.wealthora.server.config;

import com.wealthora.server.security.BackwardCompatibleBcryptPasswordEncoder;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class WealthoraBeans {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BackwardCompatibleBcryptPasswordEncoder(12);
    }
}
