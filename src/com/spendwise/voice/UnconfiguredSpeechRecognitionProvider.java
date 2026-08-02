package com.spendwise.voice;

public final class UnconfiguredSpeechRecognitionProvider
        implements SpeechRecognitionProvider {

    @Override
    public String getDisplayName() {
        return "No speech provider";
    }

    @Override
    public String getStatus() {
        return "Not configured — manual command entry is available";
    }

    @Override
    public String getMicrophoneStatus() {
        return "Unavailable until a speech provider is configured";
    }

    @Override
    public SpeechProviderStatus getProviderStatus() {
        return SpeechProviderStatus.NOT_CONFIGURED;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public SpeechRecognitionResult recognize(SpeechRecognitionRequest request) {
        throw new IllegalStateException(
                "Speech recognition requires provider configuration. "
                + "Use the manual command field instead.");
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean testMicrophone() {
        return false;
    }
}
