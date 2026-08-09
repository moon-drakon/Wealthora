package com.wealthora.otp.relay;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

record RelayConfiguration(
        InetAddress bindAddress,
        int port,
        boolean loopbackHttp,
        Path keyStore,
        char[] keyStorePassword,
        byte[] signingSecret,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        char[] smtpPassword,
        String senderAddress,
        String senderName) {

    static RelayConfiguration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static RelayConfiguration fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment);
        try {
            InetAddress address = InetAddress.getByName(value(environment,
                    "WEALTHORA_RELAY_BIND_ADDRESS", "127.0.0.1"));
            int port = integer(environment, "WEALTHORA_RELAY_PORT", 8443);
            boolean loopbackHttp = Boolean.parseBoolean(value(environment,
                    "WEALTHORA_RELAY_ALLOW_HTTP_LOOPBACK", "false"));
            if (loopbackHttp && !address.isLoopbackAddress()) {
                throw new IllegalArgumentException(
                        "Plain HTTP may bind only to a loopback address.");
            }
            Path keyStore = optionalPath(environment,
                    "WEALTHORA_RELAY_KEYSTORE");
            char[] keyStorePassword = optional(environment,
                    "WEALTHORA_RELAY_KEYSTORE_PASSWORD").toCharArray();
            if (!loopbackHttp
                    && (keyStore == null || keyStorePassword.length == 0)) {
                throw new IllegalArgumentException(
                        "HTTPS requires WEALTHORA_RELAY_KEYSTORE and its password.");
            }
            byte[] secret = required(environment,
                    "WEALTHORA_OTP_SIGNING_SECRET")
                    .getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalArgumentException(
                        "WEALTHORA_OTP_SIGNING_SECRET must contain at least 32 bytes.");
            }
            String smtpHost = required(environment, "WEALTHORA_SMTP_HOST");
            int smtpPort = integer(environment, "WEALTHORA_SMTP_PORT", 587);
            String smtpUsername = required(environment,
                    "WEALTHORA_SMTP_USERNAME");
            char[] smtpPassword = required(environment,
                    "WEALTHORA_SMTP_PASSWORD").toCharArray();
            String sender = mailbox(value(environment,
                    "WEALTHORA_SMTP_FROM", smtpUsername));
            String senderName = senderName(value(environment,
                    "WEALTHORA_SMTP_FROM_NAME", "Wealthora Security"));
            return new RelayConfiguration(address, port, loopbackHttp,
                    keyStore, keyStorePassword, secret, smtpHost, smtpPort,
                    smtpUsername, smtpPassword, sender, senderName);
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException(
                    "WEALTHORA_RELAY_BIND_ADDRESS is invalid.", exception);
        }
    }

    private static int integer(
            Map<String, String> environment, String name, int fallback) {
        String value = value(environment, name, Integer.toString(fallback));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a valid port.");
        }
    }

    private static Path optionalPath(
            Map<String, String> environment, String name) {
        String value = optional(environment, name);
        return value.isEmpty() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private static String required(Map<String, String> values, String name) {
        String value = optional(values, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private static String value(
            Map<String, String> values, String name, String fallback) {
        String value = optional(values, name);
        return value.isEmpty() ? fallback : value;
    }

    private static String optional(Map<String, String> values, String name) {
        String value = values.get(name);
        return value == null ? "" : value.strip();
    }

    private static String mailbox(String value) {
        String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf('@');
        if (separator <= 0 || separator != normalized.lastIndexOf('@')
                || separator == normalized.length() - 1
                || normalized.chars().anyMatch(Character::isWhitespace)
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "WEALTHORA_SMTP_FROM must be a plain email address.");
        }
        return normalized;
    }

    private static String senderName(String value) {
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 70
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || !normalized.matches("[A-Za-z0-9 .,&()_+\\-]+")) {
            throw new IllegalArgumentException(
                    "WEALTHORA_SMTP_FROM_NAME is invalid.");
        }
        return normalized;
    }
}
