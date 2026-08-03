package com.spendwise.voice;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class AuthenticatedSpeechRecognitionProvider
        implements SpeechRecognitionProvider {

    private final SpeechApiClient client;
    private final MicrophoneCapture microphone;
    private volatile SpeechBackendStatus backendStatus =
            new SpeechBackendStatus(SpeechProviderStatus.NOT_CONFIGURED,
                    "Speech provider status has not been checked.");

    public AuthenticatedSpeechRecognitionProvider(
            SpeechApiClient client, MicrophoneCapture microphone) {
        this.client = Objects.requireNonNull(client);
        this.microphone = Objects.requireNonNull(microphone);
    }

    @Override
    public String getDisplayName() {
        return "Google Cloud Speech-to-Text V1";
    }

    @Override
    public String getStatus() {
        if (!client.hasActiveSession()) {
            return "Sign in with an online account to use speech recognition.";
        }
        if (microphone.listMicrophones().isEmpty()) {
            return microphone.getStatus();
        }
        return backendStatus.message();
    }

    @Override
    public String getMicrophoneStatus() {
        return microphone.getStatus();
    }

    @Override
    public SpeechProviderStatus getProviderStatus() {
        if (!client.hasActiveSession()) {
            return SpeechProviderStatus.NOT_CONFIGURED;
        }
        if (microphone.listMicrophones().isEmpty()) {
            return SpeechProviderStatus.UNAVAILABLE;
        }
        return backendStatus.status();
    }

    @Override
    public boolean isConfigured() {
        return getProviderStatus() == SpeechProviderStatus.READY;
    }

    @Override
    public void refreshStatus() {
        backendStatus = client.hasActiveSession()
                ? client.getSpeechStatus()
                : new SpeechBackendStatus(SpeechProviderStatus.NOT_CONFIGURED,
                        "Sign in with an online account to use speech recognition.");
    }

    @Override
    public SpeechRecognitionResult recognize(
            SpeechRecognitionRequest request) {
        Objects.requireNonNull(request);
        if (!isConfigured()) {
            throw new IllegalStateException(getStatus());
        }
        CapturedAudio captured = microphone.capture(request.timeout());
        byte[] audio = captured.linearPcm();
        try {
            if (audio.length < 3_200) {
                throw new IllegalStateException(
                        "The recording was too short to recognize.");
            }
            return client.recognizeSpeech(audio,
                    JavaSoundMicrophoneCapture.SAMPLE_RATE_HERTZ,
                    request.language());
        } finally {
            Arrays.fill(audio, (byte) 0);
        }
    }

    @Override
    public void stop() {
        microphone.stop();
    }

    @Override
    public void cancel() {
        microphone.cancel();
    }

    @Override
    public List<MicrophoneDevice> listMicrophones() {
        return microphone.listMicrophones();
    }

    @Override
    public void selectMicrophone(String identifier) {
        microphone.selectMicrophone(identifier);
    }

    @Override
    public String getSelectedMicrophoneIdentifier() {
        return microphone.getSelectedMicrophoneIdentifier();
    }

    @Override
    public Duration getRecordingDuration() {
        return microphone.getRecordingDuration();
    }

    @Override
    public boolean testMicrophone() {
        return microphone.testMicrophone();
    }
}
