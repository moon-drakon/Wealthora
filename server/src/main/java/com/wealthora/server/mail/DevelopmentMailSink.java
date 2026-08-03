package com.wealthora.server.mail;

import com.wealthora.server.api.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("dev-mail-sink")
public final class DevelopmentMailSink implements
        VerificationMailDelivery, PasswordResetMailDelivery {

    private final Path directory;

    public DevelopmentMailSink(
            @Value("${wealthora.mail.development-directory}") String path) {
        directory = Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public synchronized void sendVerificationCode(
            String recipient, String code) {
        String safeName = recipient.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(safeName + ".txt"),
                    "Development-only Wealthora verification\nemail="
                            + recipient + "\ncode=" + code + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DEVELOPMENT_MAIL_SINK_FAILED",
                    "The development mail sink is unavailable.");
        }
    }

    @Override
    public synchronized void sendPasswordResetToken(
            String recipient, String token) {
        String safeName = recipient.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(
                    safeName + ".reset.txt"),
                    "Development-only Wealthora password reset\nemail="
                            + recipient + "\ntoken=" + token + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DEVELOPMENT_MAIL_SINK_FAILED",
                    "The development mail sink is unavailable.");
        }
    }
}
