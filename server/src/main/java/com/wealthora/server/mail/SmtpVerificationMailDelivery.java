package com.wealthora.server.mail;

import com.wealthora.server.api.ApiException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Profile("!dev-mail-sink")
public final class SmtpVerificationMailDelivery
        implements VerificationMailDelivery, PasswordResetMailDelivery {

    private final JavaMailSender mailSender;
    private final String host;
    private final String fromAddress;
    private final String fromName;
    private final boolean credentialsConfigured;

    public SmtpVerificationMailDelivery(
            JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${wealthora.mail.from-address:}") String fromAddress,
            @Value("${wealthora.mail.from-name:Wealthora}") String fromName) {
        this.mailSender = mailSender;
        this.host = host == null ? "" : host.strip();
        this.fromAddress = fromAddress == null ? "" : fromAddress.strip();
        this.fromName = fromName == null || fromName.isBlank()
                ? "Wealthora" : fromName.strip();
        credentialsConfigured = username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    @Override
    public boolean isAvailable() {
        return !host.isBlank() && !fromAddress.isBlank()
                && credentialsConfigured;
    }

    @Override
    public void sendVerificationCode(String recipient, String code) {
        requireConfigured();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipient);
            helper.setSubject("Verify your Wealthora account");
            helper.setText("Your Wealthora verification code is " + code
                    + ". It expires shortly. If you did not request this, ignore this message.");
            mailSender.send(message);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_DELIVERY_FAILED",
                    "The verification email could not be delivered. Try again later.");
        }
    }

    @Override
    public void sendPasswordResetToken(String recipient, String token) {
        requireConfigured();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipient);
            helper.setSubject("Reset your Wealthora password");
            helper.setText("Use this one-time Wealthora reset token: " + token
                    + ". It expires shortly. If you did not request this, ignore this message.");
            mailSender.send(message);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_DELIVERY_FAILED",
                    "The password-reset email could not be delivered. Try again later.");
        }
    }

    private void requireConfigured() {
        if (!isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_NOT_CONFIGURED",
                    "Email delivery is not configured on this server.");
        }
    }
}
