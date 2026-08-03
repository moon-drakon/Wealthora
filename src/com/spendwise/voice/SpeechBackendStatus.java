package com.spendwise.voice;

public record SpeechBackendStatus(
        SpeechProviderStatus status, String message) {

    public SpeechBackendStatus {
        if (status == null) {
            throw new IllegalArgumentException("Speech status is required.");
        }
        message = message == null || message.isBlank()
                ? "Speech recognition is unavailable."
                : message.strip();
    }

    public boolean ready() {
        return status == SpeechProviderStatus.READY;
    }
}
