package com.wealthora.otp.relay;

final class RateLimitException extends RuntimeException {

    RateLimitException() {
        super("OTP request rate exceeded.");
    }
}
