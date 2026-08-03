package com.wealthora.server.mail;

public interface VerificationMailDelivery {

    boolean isAvailable();

    void sendVerificationCode(String recipient, String code);
}
