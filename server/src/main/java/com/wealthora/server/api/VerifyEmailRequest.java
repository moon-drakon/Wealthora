package com.wealthora.server.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {

    @Override
    public String toString() {
        return "VerifyEmailRequest[email=" + email + ", code=[REDACTED]]";
    }
}
