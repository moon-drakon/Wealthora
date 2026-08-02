package com.spendwise.voice;

public interface SpeechRecognitionProvider {

    String getDisplayName();

    String getStatus();

    String getMicrophoneStatus();

    SpeechProviderStatus getProviderStatus();

    boolean isConfigured();

    SpeechRecognitionResult recognize(SpeechRecognitionRequest request);

    default SpeechRecognitionResult recognize(VoiceInputLanguage language) {
        return recognize(SpeechRecognitionRequest.forLanguage(language));
    }

    void stop();

    boolean testMicrophone();
}
