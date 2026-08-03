package com.wealthora.server.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmtpVerificationMailDeliveryTest {

    @Test
    void availabilityRequiresAllProductionSmtpSettings() {
        assertFalse(delivery("", "user", "secret", "from@example.com")
                .isAvailable());
        assertFalse(delivery("smtp.example.com", "", "secret",
                "from@example.com").isAvailable());
        assertFalse(delivery("smtp.example.com", "user", "",
                "from@example.com").isAvailable());
        assertFalse(delivery("smtp.example.com", "user", "secret", "")
                .isAvailable());
        assertTrue(delivery("smtp.example.com", "user", "secret",
                "from@example.com").isAvailable());
    }

    private static SmtpVerificationMailDelivery delivery(
            String host, String username, String password, String from) {
        return new SmtpVerificationMailDelivery(
                null, host, username, password, from, "Wealthora");
    }
}
