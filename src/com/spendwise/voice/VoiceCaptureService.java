package com.spendwise.voice;

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
        return provider.recognize(settings.getPreferredLanguage());
    }

    public void stop() {
        provider.stop();
    }

    public SpeechRecognitionProvider getProvider() {
        return provider;
    }
}
