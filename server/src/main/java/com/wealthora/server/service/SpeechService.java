package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.SpeechInputLanguage;
import com.wealthora.server.api.SpeechRecognitionRequest;
import com.wealthora.server.api.SpeechRecognitionResponse;
import com.wealthora.server.api.SpeechStatusResponse;
import com.wealthora.server.config.SpeechProperties;
import com.wealthora.server.speech.CloudSpeechResult;
import com.wealthora.server.speech.SpeechBackendStatus;
import com.wealthora.server.speech.SpeechRecognitionGateway;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class SpeechService {

    private static final int REQUIRED_SAMPLE_RATE = 16_000;
    private final SpeechProperties properties;
    private final SpeechRecognitionGateway gateway;

    public SpeechService(
            SpeechProperties properties,
            SpeechRecognitionGateway gateway) {
        this.properties = properties;
        this.gateway = gateway;
    }

    public SpeechStatusResponse status() {
        SpeechBackendStatus status = gateway.status(properties.projectId());
        return new SpeechStatusResponse("Google Cloud Speech-to-Text",
                "V1", status.ready(), status.message());
    }

    public SpeechRecognitionResponse recognize(
            SpeechRecognitionRequest request) {
        if (request.sampleRateHertz() != REQUIRED_SAMPLE_RATE) {
            throw badRequest("Speech audio must use a 16000 Hz sample rate.");
        }
        byte[] audio = decode(request.audioBase64());
        try {
            validateSize(audio);
            SpeechBackendStatus status = gateway.status(properties.projectId());
            if (!status.ready()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "SPEECH_NOT_CONFIGURED", status.message());
            }
            CloudSpeechResult result;
            try {
                result = gateway.recognize(properties.projectId(), audio,
                        REQUIRED_SAMPLE_RATE,
                        request.language().primaryLocale(),
                        request.language().alternativeLocales());
            } catch (RuntimeException exception) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "SPEECH_RECOGNITION_FAILED",
                        "Google Cloud could not recognize this recording. Check the Speech-to-Text API and server credentials.");
            }
            return new SpeechRecognitionResponse(result.transcript(),
                    result.confidence(), language(result.detectedLocale()),
                    result.detectedLocale(), durationMilliseconds(audio.length));
        } finally {
            java.util.Arrays.fill(audio, (byte) 0);
        }
    }

    private byte[] decode(String encoded) {
        if (encoded.length() > ((properties.maximumAudioBytes() + 2) / 3) * 4 + 8) {
            throw badRequest("The recording exceeds the maximum allowed duration.");
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw badRequest("Speech audio is not valid Base64 data.");
        }
    }

    private void validateSize(byte[] audio) {
        if (audio.length < properties.minimumAudioBytes()) {
            throw badRequest("The recording is too short to recognize.");
        }
        if (audio.length > properties.maximumAudioBytes()) {
            throw badRequest("The recording exceeds the maximum allowed duration.");
        }
        if ((audio.length & 1) != 0) {
            throw badRequest("Speech audio must contain complete 16-bit samples.");
        }
    }

    private static long durationMilliseconds(int bytes) {
        return Math.round(bytes * 1000.0 / (REQUIRED_SAMPLE_RATE * 2));
    }

    private static String language(String locale) {
        return locale != null && locale.toLowerCase().startsWith("bn")
                ? SpeechInputLanguage.BANGLA.name()
                : SpeechInputLanguage.ENGLISH.name();
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_SPEECH_AUDIO", message);
    }
}
