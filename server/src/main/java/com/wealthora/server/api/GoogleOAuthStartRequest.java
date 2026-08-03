package com.wealthora.server.api;

import jakarta.validation.constraints.Size;

public record GoogleOAuthStartRequest(
        @Size(max = 160) String deviceLabel) {
}
