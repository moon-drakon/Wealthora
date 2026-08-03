package com.wealthora.server.mail;

public interface PasswordResetMailDelivery {

    void sendPasswordResetToken(String recipient, String token);
}
