package com.wealthora.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminActionRequest(
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 72) char[] currentPassword) {

    @Override
    public String toString() {
        return "AdminActionRequest[reason=" + reason
                + ", currentPassword=[REDACTED]]";
    }
}
