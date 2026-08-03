package com.wealthora.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank String email,
        @NotNull @Size(min = 1, max = 128) char[] password,
        String deviceLabel) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email
                + ", password=[REDACTED], deviceLabel=" + deviceLabel + "]";
    }
}
