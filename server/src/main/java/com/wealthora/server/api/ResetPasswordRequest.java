package com.wealthora.server.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotNull char[] resetToken,
        @NotNull char[] newPassword,
        @NotNull char[] passwordConfirmation) {
}
