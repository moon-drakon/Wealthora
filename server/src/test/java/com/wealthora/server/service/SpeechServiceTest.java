package com.wealthora.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.SpeechInputLanguage;
import com.wealthora.server.api.SpeechRecognitionRequest;
import com.wealthora.server.api.SpeechRecognitionResponse;
import com.wealthora.server.config.SpeechProperties;
import com.wealthora.server.speech.CloudSpeechResult;
import com.wealthora.server.speech.SpeechBackendStatus;
import com.wealthora.server.speech.SpeechRecognitionGateway;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpeechServiceTest {

    private static final SpeechProperties PROPERTIES =
            new SpeechProperties("wealthora-voice", 1_000_000, 3_200);

    @Test
    void recognitionWipesDecodedAudioAfterSuccess() {
        CapturingGateway gateway = new CapturingGateway();
        SpeechService service = new SpeechService(PROPERTIES, gateway);

        SpeechRecognitionResponse response = service.recognize(
                request(new byte[3_200]));

        assertEquals("Paid 500 taka for lunch", response.transcript());
        assertEquals("ENGLISH", response.detectedLanguage());
        assertEquals(100, response.audioDurationMilliseconds());
        assertTrue(gateway.receivedAudio);
        assertAllZero(gateway.audioReference);
    }

    @Test
    void recognitionWipesDecodedAudioAfterProviderFailure() {
        CapturingGateway gateway = new CapturingGateway();
        gateway.failRecognition = true;
        SpeechService service = new SpeechService(PROPERTIES, gateway);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.recognize(request(filledAudio())));

        assertEquals("SPEECH_RECOGNITION_FAILED", failure.getCode());
        assertTrue(gateway.receivedAudio);
        assertAllZero(gateway.audioReference);
    }

    @Test
    void unavailableProviderIsReportedWithoutRecognition() {
        CapturingGateway gateway = new CapturingGateway();
        gateway.ready = false;
        SpeechService service = new SpeechService(PROPERTIES, gateway);

        assertFalse(service.status().ready());
        ApiException failure = assertThrows(ApiException.class,
                () -> service.recognize(request(filledAudio())));

        assertEquals("SPEECH_NOT_CONFIGURED", failure.getCode());
        assertFalse(gateway.receivedAudio);
    }

    @Test
    void invalidAudioIsRejectedBeforeProviderCall() {
        CapturingGateway gateway = new CapturingGateway();
        SpeechService service = new SpeechService(PROPERTIES, gateway);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.recognize(request(new byte[10])));

        assertEquals("INVALID_SPEECH_AUDIO", failure.getCode());
        assertFalse(gateway.receivedAudio);
    }

    private static SpeechRecognitionRequest request(byte[] audio) {
        return new SpeechRecognitionRequest(
                Base64.getEncoder().encodeToString(audio),
                16_000,
                SpeechInputLanguage.AUTOMATIC);
    }

    private static byte[] filledAudio() {
        byte[] audio = new byte[3_200];
        java.util.Arrays.fill(audio, (byte) 7);
        return audio;
    }

    private static void assertAllZero(byte[] audio) {
        for (byte value : audio) assertEquals((byte) 0, value);
    }

    private static final class CapturingGateway
            implements SpeechRecognitionGateway {

        private boolean ready = true;
        private boolean failRecognition;
        private boolean receivedAudio;
        private byte[] audioReference;

        @Override
        public SpeechBackendStatus status(String projectId) {
            return new SpeechBackendStatus(ready,
                    ready ? "Test provider ready."
                            : "Test provider unavailable.");
        }

        @Override
        public CloudSpeechResult recognize(
                String projectId,
                byte[] linearPcmAudio,
                int sampleRateHertz,
                String primaryLocale,
                List<String> alternativeLocales) {
            receivedAudio = true;
            audioReference = linearPcmAudio;
            if (failRecognition) {
                throw new IllegalStateException(
                        "Synthetic recognition failure.");
            }
            return new CloudSpeechResult(
                    "Paid 500 taka for lunch", 0.91, "en-US");
        }
    }
}
