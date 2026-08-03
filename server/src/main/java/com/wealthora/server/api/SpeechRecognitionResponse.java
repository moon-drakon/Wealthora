package com.wealthora.server.api;

public record SpeechRecognitionResponse(
        String transcript,
        double confidence,
        String detectedLanguage,
        String detectedLocale,
        long audioDurationMilliseconds) {
}
