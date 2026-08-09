package com.wealthora.otp.relay;

interface MailDelivery {

    void sendVerificationCode(
            String recipient, String code, OtpPurpose purpose);
}
