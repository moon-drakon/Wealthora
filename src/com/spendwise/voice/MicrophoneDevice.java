package com.spendwise.voice;

import java.util.Objects;

public record MicrophoneDevice(String identifier, String displayName) {

    public MicrophoneDevice {
        identifier = Objects.requireNonNull(identifier).strip();
        displayName = Objects.requireNonNull(displayName).strip();
        if (identifier.isEmpty() || displayName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Microphone identifier and name are required.");
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
