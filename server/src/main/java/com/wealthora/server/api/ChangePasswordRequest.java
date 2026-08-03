package com.wealthora.server.api;

import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(
        @NotNull char[] currentPassword,
        @NotNull char[] newPassword,
        @NotNull char[] passwordConfirmation) {
}
