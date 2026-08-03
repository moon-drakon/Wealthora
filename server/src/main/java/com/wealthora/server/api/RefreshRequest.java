package com.wealthora.server.api;

import jakarta.validation.constraints.NotNull;

public record RefreshRequest(@NotNull char[] refreshToken) {
}
