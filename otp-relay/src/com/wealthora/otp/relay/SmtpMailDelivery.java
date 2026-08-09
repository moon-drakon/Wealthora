package com.wealthora.otp.relay;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class SmtpMailDelivery implements MailDelivery {

    private static final int TIMEOUT_MILLIS = 8_000;
    private final String host;
    private final int port;
    private final String username;
    private final char[] password;
    private final String sender;
    private final String senderName;

    SmtpMailDelivery(RelayConfiguration configuration) {
        this.host = configuration.smtpHost();
        this.port = configuration.smtpPort();
        this.username = configuration.smtpUsername();
        this.password = configuration.smtpPassword().clone();
        this.sender = configuration.senderAddress();
        this.senderName = configuration.senderName();
    }

    @Override
    public void sendVerificationCode(
            String recipient, String code, OtpPurpose purpose) {
        Objects.requireNonNull(purpose);
        try (Socket plain = new Socket()) {
            plain.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
            plain.setSoTimeout(TIMEOUT_MILLIS);
            Session session = new Session(plain);
            session.expect(220);
            session.command("EHLO wealthora-otp-relay", 250);
            session.command("STARTTLS", 220);

            SSLSocketFactory factory =
                    (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket secure = (SSLSocket) factory.createSocket(
                    plain, host, port, true)) {
                SSLParameters parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                secure.setSSLParameters(parameters);
                secure.setSoTimeout(TIMEOUT_MILLIS);
                secure.startHandshake();
                Session tls = new Session(secure);
                tls.command("EHLO wealthora-otp-relay", 250);
                tls.command("AUTH LOGIN", 334);
                tls.command(base64(username), 334);
                tls.command(base64(new String(password)), 235);
                tls.command("MAIL FROM:<" + sender + ">", 250);
                tls.command("RCPT TO:<" + recipient + ">", 250, 251);
                tls.command("DATA", 354);
                tls.writeData(message(recipient, code, purpose));
                tls.expect(250);
                tls.command("QUIT", 221);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Verification email delivery is unavailable.", exception);
        }
    }

    private String message(
            String recipient, String code, OtpPurpose purpose) {
        String boundary = "wealthora_alt_"
                + UUID.randomUUID().toString().replace("-", "");
        return OtpEmailTemplate.multipartMessage(
                sender, senderName, recipient, code, purpose, boundary);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Session {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        Session(Socket socket) throws IOException {
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        void command(String command, int... accepted) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            expect(accepted);
        }

        void writeData(String message) throws IOException {
            for (String line : message.split("\\r?\\n", -1)) {
                if (line.startsWith(".")) {
                    writer.write('.');
                }
                writer.write(line);
                writer.write("\r\n");
            }
            writer.write(".\r\n");
            writer.flush();
        }

        void expect(int... accepted) throws IOException {
            String line = reader.readLine();
            if (line == null || line.length() < 3) {
                throw new IOException("SMTP server closed the connection.");
            }
            String code = line.substring(0, 3);
            while (line.length() > 3 && line.charAt(3) == '-') {
                line = reader.readLine();
                if (line == null) {
                    throw new IOException("SMTP response was incomplete.");
                }
                if (line.startsWith(code + " ")) {
                    break;
                }
            }
            int actual;
            try {
                actual = Integer.parseInt(code);
            } catch (NumberFormatException exception) {
                throw new IOException("SMTP response was invalid.");
            }
            for (int expected : accepted) {
                if (actual == expected) {
                    return;
                }
            }
            throw new IOException("SMTP command was rejected with status "
                    + actual + ".");
        }
    }
}
