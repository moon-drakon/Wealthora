package com.spendwise.voice;

public interface SpeechApiClient {

    default SpeechBackendStatus getSpeechStatus() {
        return new SpeechBackendStatus(SpeechProviderStatus.NOT_CONFIGURED,
                "An authenticated online session is required for speech recognition.");
    }

    default SpeechRecognitionResult recognizeSpeech(
            byte[] linearPcmAudio,
            int sampleRateHertz,
            VoiceInputLanguage language) {
        throw new IllegalStateException(
                "An authenticated online session is required for speech recognition.");
    }

    boolean hasActiveSession();
}
