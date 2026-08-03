package com.wealthora.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank String email,
        @NotNull char[] password,
        String deviceLabel) {
}
