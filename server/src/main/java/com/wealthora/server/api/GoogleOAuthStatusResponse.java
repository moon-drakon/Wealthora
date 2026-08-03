package com.wealthora.server.api;

public record GoogleOAuthStatusResponse(
        boolean configured, String message, String redirectUri) {
}
