package com.wealthora.server.api;

public record AuthenticationAvailabilityResponse(
        boolean emailProviderAvailable,
        boolean googleOAuthAvailable) {
}
