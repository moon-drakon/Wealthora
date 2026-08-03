package com.wealthora.server.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotNull @Size(min = 32, max = 200) char[] resetToken,
        @NotNull @Size(min = 8, max = 128) char[] newPassword,
        @NotNull @Size(min = 8, max = 128) char[] passwordConfirmation) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email
                + ", resetToken=[REDACTED], newPassword=[REDACTED], "
                + "passwordConfirmation=[REDACTED]]";
    }
}
