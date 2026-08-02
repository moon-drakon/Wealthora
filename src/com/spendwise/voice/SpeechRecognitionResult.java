package com.spendwise.voice;

import java.util.Objects;

public record SpeechRecognitionResult(
        String transcript,
        double confidence,
        VoiceInputLanguage detectedLanguage) {

    public SpeechRecognitionResult {
        transcript = Objects.requireNonNull(
                transcript, "Speech transcript is required.").strip();
        if (transcript.isEmpty()) {
            throw new IllegalArgumentException(
                    "Speech transcript cannot be blank.");
        }
        if (Double.isNaN(confidence)
                || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Speech confidence must be between 0 and 1.");
        }
        Objects.requireNonNull(
                detectedLanguage, "Detected language is required.");
    }
}
