package com.spendwise.voice;

import java.time.Duration;
import java.util.List;

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

    default void cancel() {
        stop();
    }

    default void refreshStatus() {
    }

    default List<MicrophoneDevice> listMicrophones() {
        return List.of();
    }

    default void selectMicrophone(String identifier) {
        throw new IllegalStateException(getMicrophoneStatus());
    }

    default String getSelectedMicrophoneIdentifier() {
        return null;
    }

    default Duration getRecordingDuration() {
        return Duration.ZERO;
    }

    boolean testMicrophone();
}
