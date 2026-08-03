package com.wealthora.server.speech;

public record CloudSpeechResult(
        String transcript, double confidence, String detectedLocale) {
}
