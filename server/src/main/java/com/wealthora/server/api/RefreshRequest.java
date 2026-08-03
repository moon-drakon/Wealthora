package com.wealthora.server.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotNull @Size(min = 32, max = 200) char[] refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=[REDACTED]]";
    }
}
