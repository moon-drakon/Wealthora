package com.spendwise.voice;

import java.time.Duration;
import java.util.Objects;

/** Secret-free recognition options passed to a configured speech provider. */
public record SpeechRecognitionRequest(
        VoiceInputLanguage language,
        String localeTag,
        Duration timeout,
        boolean allowLanguageDetection,
        boolean storeAudio) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public SpeechRecognitionRequest {
        Objects.requireNonNull(language, "Input language is required.");
        localeTag = Objects.requireNonNull(
                localeTag, "Locale tag is required.").strip();
        if (localeTag.isEmpty()) {
            throw new IllegalArgumentException("Locale tag is required.");
        }
        Objects.requireNonNull(timeout, "Recognition timeout is required.");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Recognition timeout must be positive.");
        }
        if (storeAudio) {
            throw new IllegalArgumentException(
                    "Wealthora does not permit speech providers to store audio.");
        }
    }

    public static SpeechRecognitionRequest forLanguage(
            VoiceInputLanguage language) {
        Objects.requireNonNull(language, "Input language is required.");
        return new SpeechRecognitionRequest(language, switch (language) {
            case ENGLISH -> "en-US";
            case BANGLA -> "bn-BD";
            case BANGLISH_MIXED, AUTOMATIC -> "en-US";
        }, DEFAULT_TIMEOUT,
                language == VoiceInputLanguage.AUTOMATIC
                || language == VoiceInputLanguage.BANGLISH_MIXED, false);
    }
}
