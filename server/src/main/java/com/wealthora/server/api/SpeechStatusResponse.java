package com.wealthora.server.api;

public record SpeechStatusResponse(
        String provider, String apiVersion, boolean ready, String message) {
}
