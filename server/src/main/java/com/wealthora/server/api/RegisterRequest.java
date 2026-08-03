package com.wealthora.server.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 80) String studentId,
        @NotNull @Size(min = 12, max = 72) char[] password,
        @NotNull @Size(min = 12, max = 72) char[] passwordConfirmation,
        @AssertTrue boolean termsAccepted) {
}
