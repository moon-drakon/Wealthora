package com.wealthora.otp.relay;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Builds the finance-independent OTP relay's plain-text and HTML email. */
final class OtpEmailTemplate {

    private static final Base64.Encoder MIME_ENCODER =
            Base64.getMimeEncoder(76, new byte[]{'\r', '\n'});

    private OtpEmailTemplate() {
    }

    static EmailContent render(String code, OtpPurpose purpose) {
        String requiredCode = Objects.requireNonNull(
                code, "Verification code is required.");
        if (!requiredCode.matches("[0-9]{6}")) {
            throw new IllegalArgumentException(
                    "Verification code must contain six digits.");
        }
        PurposeContent content = contentFor(
                Objects.requireNonNull(purpose, "OTP purpose is required."));
        String plainText = "Wealthora " + content.label() + "\r\n"
                + "Take Control of Every Taka.\r\n\r\n"
                + content.introduction() + "\r\n\r\n"
                + "Verification code: " + requiredCode + "\r\n\r\n"
                + "This code expires in 10 minutes and can be used once.\r\n"
                + "Do not share this code with anyone.\r\n\r\n"
                + "If you did not request this, ignore this email. "
                + "No changes will be made to your Wealthora account.\r\n";
        String htmlCode = escapeHtml(requiredCode);
        String html = "<!doctype html>"
                + "<html lang=\"en\"><head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,"
                + " initial-scale=1.0\">"
                + "<title>" + escapeHtml(content.subject()) + "</title>"
                + "</head>"
                + "<body style=\"margin:0;padding:0;background-color:#f3f7f5;"
                + "font-family:Arial,Helvetica,sans-serif;color:#17212b;\">"
                + "<table role=\"presentation\" width=\"100%\""
                + " cellspacing=\"0\" cellpadding=\"0\" border=\"0\""
                + " style=\"width:100%;background-color:#f3f7f5;\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"600\""
                + " cellspacing=\"0\" cellpadding=\"0\" border=\"0\""
                + " style=\"width:100%;max-width:600px;background-color:#ffffff;"
                + "border:1px solid #d8e3de;border-radius:12px;"
                + "border-collapse:separate;overflow:hidden;\">"
                + "<tr><td style=\"padding:28px 32px;background-color:#12664f;"
                + "color:#ffffff;\">"
                + "<div style=\"font-size:30px;line-height:36px;font-weight:700;"
                + "letter-spacing:0.2px;\">Wealthora</div>"
                + "<div style=\"padding-top:5px;font-size:15px;line-height:22px;"
                + "color:#dff3eb;\">Take Control of Every Taka.</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:34px 32px 30px 32px;\">"
                + "<h1 style=\"margin:0 0 14px 0;font-size:26px;line-height:34px;"
                + "font-weight:700;color:#12664f;\">"
                + escapeHtml(content.heading()) + "</h1>"
                + "<p style=\"margin:0 0 24px 0;font-size:16px;line-height:25px;"
                + "color:#394650;\">" + escapeHtml(content.introduction())
                + "</p>"
                + "<table role=\"presentation\" width=\"100%\""
                + " cellspacing=\"0\" cellpadding=\"0\" border=\"0\""
                + " style=\"width:100%;border-collapse:separate;\">"
                + "<tr><td align=\"center\" style=\"padding:22px 12px;"
                + "background-color:#e8f4ef;border:1px solid #b8d8cb;"
                + "border-radius:10px;\">"
                + "<div style=\"font-family:'Courier New',Courier,monospace;"
                + "font-size:36px;line-height:44px;font-weight:700;"
                + "letter-spacing:8px;color:#0b4f3d;\">" + htmlCode
                + "</div></td></tr></table>"
                + "<p style=\"margin:22px 0 8px 0;font-size:15px;line-height:23px;"
                + "color:#394650;\"><strong>This code expires in 10 minutes"
                + "</strong> and can be used once.</p>"
                + "<p style=\"margin:0 0 22px 0;font-size:15px;line-height:23px;"
                + "color:#b42318;\"><strong>Do not share this code with anyone."
                + "</strong></p>"
                + "<table role=\"presentation\" width=\"100%\""
                + " cellspacing=\"0\" cellpadding=\"0\" border=\"0\""
                + " style=\"width:100%;border-collapse:separate;\">"
                + "<tr><td style=\"padding:16px 18px;background-color:#f7f9f8;"
                + "border-left:4px solid #d2a11e;font-size:14px;line-height:22px;"
                + "color:#4b5861;\">If you did not request this, ignore this"
                + " email. No changes will be made to your Wealthora account."
                + "</td></tr></table>"
                + "</td></tr>"
                + "<tr><td style=\"padding:18px 32px;background-color:#eef4f1;"
                + "font-size:12px;line-height:18px;color:#66736d;\">"
                + "This automated security message contains no password or"
                + " financial information.</td></tr>"
                + "</table></td></tr></table></body></html>";
        return new EmailContent(content.subject(), plainText, html);
    }

    static String multipartMessage(
            String sender,
            String senderName,
            String recipient,
            String code,
            OtpPurpose purpose,
            String boundary) {
        String safeSender = requireMailbox(sender, "Sender");
        String safeSenderName = requireSenderName(senderName);
        String safeRecipient = requireMailbox(recipient, "Recipient");
        String safeBoundary = Objects.requireNonNull(
                boundary, "MIME boundary is required.");
        if (!safeBoundary.matches("[A-Za-z0-9_-]{12,70}")) {
            throw new IllegalArgumentException("MIME boundary is invalid.");
        }
        EmailContent content = render(code, purpose);
        return "From: " + safeSenderName + " <" + safeSender + ">\r\n"
                + "To: <" + safeRecipient + ">\r\n"
                + "Subject: " + content.subject() + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/alternative;\r\n"
                + " boundary=\"" + safeBoundary + "\"\r\n"
                + "\r\n"
                + "This is a multipart message in MIME format.\r\n"
                + mimePart(safeBoundary, "text/plain", content.plainText())
                + mimePart(safeBoundary, "text/html", content.html())
                + "--" + safeBoundary + "--\r\n";
    }

    static String escapeHtml(String value) {
        String text = Objects.requireNonNull(value);
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String mimePart(
            String boundary, String contentType, String content) {
        String encoded = MIME_ENCODER.encodeToString(
                content.getBytes(StandardCharsets.UTF_8));
        return "--" + boundary + "\r\n"
                + "Content-Type: " + contentType + "; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + encoded + "\r\n";
    }

    private static PurposeContent contentFor(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> new PurposeContent(
                    "Email Verification",
                    "Verify your Wealthora email",
                    "Verify your email address",
                    "Use this verification code to finish creating your "
                    + "Wealthora account.");
            case PASSWORD_RESET -> new PurposeContent(
                    "Password Reset",
                    "Reset your Wealthora password",
                    "Reset your password",
                    "Use this verification code to continue resetting your "
                    + "Wealthora password.");
        };
    }

    private static String requireMailbox(String value, String label) {
        String mailbox = Objects.requireNonNull(value, label + " is required.")
                .strip();
        if (!mailbox.matches(
                "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException(label + " address is invalid.");
        }
        return mailbox;
    }

    private static String requireSenderName(String value) {
        String senderName = Objects.requireNonNull(
                value, "Sender name is required.").strip();
        if (senderName.isEmpty() || senderName.length() > 70
                || !senderName.matches("[A-Za-z0-9 .,&()_+\\-]+")) {
            throw new IllegalArgumentException("Sender name is invalid.");
        }
        return senderName;
    }

    record EmailContent(String subject, String plainText, String html) {
    }

    private record PurposeContent(
            String label,
            String subject,
            String heading,
            String introduction) {
    }
}
