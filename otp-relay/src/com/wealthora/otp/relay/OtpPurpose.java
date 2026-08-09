package com.wealthora.otp.relay;

enum OtpPurpose {
    REGISTRATION("complete your Wealthora registration"),
    PASSWORD_RESET("reset your Wealthora password");

    private final String action;

    OtpPurpose(String action) {
        this.action = action;
    }

    String action() {
        return action;
    }

    static OtpPurpose parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException("Unsupported OTP purpose.");
        }
    }
}
