package com.wealthora.server.mail;

import com.wealthora.server.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("!dev-mail-sink")
public final class SmtpVerificationMailDelivery
        implements VerificationMailDelivery {

    private final JavaMailSender mailSender;
    private final String host;
    private final String from;

    public SmtpVerificationMailDelivery(
            JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${wealthora.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.host = host == null ? "" : host.strip();
        this.from = from == null ? "" : from.strip();
    }

    @Override
    public void sendVerificationCode(String recipient, String code) {
        if (host.isBlank() || from.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_NOT_CONFIGURED",
                    "Email delivery is not configured on this server.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Verify your Wealthora account");
        message.setText("Your Wealthora verification code is " + code
                + ". It expires shortly. If you did not request this, ignore this message.");
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_DELIVERY_FAILED",
                    "The verification email could not be delivered. Try again later.");
        }
    }
}
