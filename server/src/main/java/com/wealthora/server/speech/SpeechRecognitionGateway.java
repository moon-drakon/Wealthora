package com.wealthora.server.speech;

import java.util.List;

public interface SpeechRecognitionGateway {

    SpeechBackendStatus status(String projectId);

    CloudSpeechResult recognize(
            String projectId,
            byte[] linearPcmAudio,
            int sampleRateHertz,
            String primaryLocale,
            List<String> alternativeLocales);
}
