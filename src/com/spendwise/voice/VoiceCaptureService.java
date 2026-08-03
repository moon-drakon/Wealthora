package com.spendwise.voice;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class VoiceCaptureService {

    private final SpeechRecognitionProvider provider;
    private final VoiceEntrySettings settings;

    public VoiceCaptureService(
            SpeechRecognitionProvider provider,
            VoiceEntrySettings settings) {
        this.provider = Objects.requireNonNull(
                provider, "Speech provider is required.");
        this.settings = Objects.requireNonNull(
                settings, "Voice settings are required.");
    }

    public SpeechRecognitionResult capture() {
        if (!settings.isEnabled()) {
            throw new IllegalStateException(
                    "Voice Quick Entry is disabled in Settings.");
        }
        if (!provider.isConfigured()) {
            throw new IllegalStateException(provider.getStatus());
        }
        SpeechRecognitionRequest request = SpeechRecognitionRequest.forLanguage(
                settings.getPreferredLanguage());
        return provider.recognize(request);
    }

    public void stop() {
        provider.stop();
    }

    public void cancel() {
        provider.cancel();
    }

    public void refreshStatus() {
        provider.refreshStatus();
    }

    public List<MicrophoneDevice> listMicrophones() {
        return provider.listMicrophones();
    }

    public void selectMicrophone(String identifier) {
        provider.selectMicrophone(identifier);
    }

    public Duration getRecordingDuration() {
        return provider.getRecordingDuration();
    }

    public SpeechRecognitionProvider getProvider() {
        return provider;
    }
}
