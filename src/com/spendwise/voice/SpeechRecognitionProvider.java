package com.spendwise.voice;

public interface SpeechRecognitionProvider {

    String getDisplayName();

    String getStatus();

    String getMicrophoneStatus();

    boolean isConfigured();

    SpeechRecognitionResult recognize(VoiceInputLanguage language);

    void stop();

    boolean testMicrophone();
}
