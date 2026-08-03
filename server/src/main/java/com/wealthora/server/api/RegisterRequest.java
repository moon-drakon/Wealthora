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
        @NotNull @Size(min = 8, max = 128) char[] password,
        @NotNull @Size(min = 8, max = 128) char[] passwordConfirmation,
        @AssertTrue boolean termsAccepted) {

    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName
                + ", email=" + email + ", studentId=" + studentId
                + ", password=[REDACTED], passwordConfirmation=[REDACTED], "
                + "termsAccepted=" + termsAccepted + "]";
    }
}
