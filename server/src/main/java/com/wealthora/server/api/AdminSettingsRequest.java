package com.wealthora.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSettingsRequest(
        boolean registrationRequiresAdminApproval,
        @NotNull @Size(min = 1, max = 72) char[] currentPassword,
        @NotBlank @Size(max = 500) String reason) {

    @Override
    public String toString() {
        return "AdminSettingsRequest[registrationRequiresAdminApproval="
                + registrationRequiresAdminApproval
                + ", currentPassword=[REDACTED], reason=" + reason + "]";
    }
}
