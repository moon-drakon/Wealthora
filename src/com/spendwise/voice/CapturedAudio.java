package com.spendwise.voice;

import java.time.Duration;
import java.util.Objects;

public record CapturedAudio(byte[] linearPcm, Duration duration) {

    public CapturedAudio {
        Objects.requireNonNull(linearPcm, "Audio is required.");
        Objects.requireNonNull(duration, "Audio duration is required.");
    }
}
