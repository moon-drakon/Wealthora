package com.wealthora.server.mail;

public interface VerificationMailDelivery {

    void sendVerificationCode(String recipient, String code);
}
