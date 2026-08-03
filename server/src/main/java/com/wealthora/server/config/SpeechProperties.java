package com.wealthora.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wealthora.speech")
public record SpeechProperties(
        String projectId,
        int maximumAudioBytes,
        int minimumAudioBytes) {

    public SpeechProperties {
        projectId = projectId == null ? "" : projectId.strip();
        if (maximumAudioBytes < 3_200) {
            maximumAudioBytes = 1_000_000;
        }
        if (minimumAudioBytes < 2 || minimumAudioBytes > maximumAudioBytes) {
            minimumAudioBytes = 3_200;
        }
    }
}
