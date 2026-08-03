package com.wealthora.server.speech;

public record SpeechBackendStatus(boolean ready, String message) {

    public SpeechBackendStatus {
        message = message == null || message.isBlank()
                ? "Speech recognition is unavailable."
                : message.strip();
    }
}
