package com.wealthora.server.api;

public record GoogleOAuthPollResponse(
        String status, String message, SessionResponse session) {
}
