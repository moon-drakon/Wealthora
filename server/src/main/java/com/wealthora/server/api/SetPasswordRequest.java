package com.wealthora.server.api;

import jakarta.validation.constraints.NotNull;

public record SetPasswordRequest(
        @NotNull char[] newPassword,
        @NotNull char[] passwordConfirmation) {
}
