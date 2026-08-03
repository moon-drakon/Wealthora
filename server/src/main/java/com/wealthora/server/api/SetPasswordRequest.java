package com.wealthora.server.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotNull @Size(min = 8, max = 128) char[] newPassword,
        @NotNull @Size(min = 8, max = 128) char[] passwordConfirmation) {

    @Override
    public String toString() {
        return "SetPasswordRequest[newPassword=[REDACTED], "
                + "passwordConfirmation=[REDACTED]]";
    }
}
