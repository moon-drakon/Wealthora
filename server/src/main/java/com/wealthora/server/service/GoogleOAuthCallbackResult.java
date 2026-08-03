package com.wealthora.server.service;

public record GoogleOAuthCallbackResult(
        boolean successful, String title, String message) {
}
