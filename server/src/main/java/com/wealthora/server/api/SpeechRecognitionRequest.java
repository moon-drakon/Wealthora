package com.wealthora.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SpeechRecognitionRequest(
        @NotBlank @Size(max = 1_333_336) String audioBase64,
        int sampleRateHertz,
        @NotNull SpeechInputLanguage language) {
}
