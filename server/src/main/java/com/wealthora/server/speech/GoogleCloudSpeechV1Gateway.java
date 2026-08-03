package com.wealthora.server.speech;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class GoogleCloudSpeechV1Gateway
        implements SpeechRecognitionGateway {

    private static final String CLOUD_PLATFORM_SCOPE =
            "https://www.googleapis.com/auth/cloud-platform";

    @Override
    public SpeechBackendStatus status(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return new SpeechBackendStatus(false,
                    "GOOGLE_CLOUD_PROJECT is not configured on the server.");
        }
        try (SpeechClient ignored = createClient()) {
            return new SpeechBackendStatus(true,
                    "Google Cloud Speech-to-Text V1 is ready.");
        } catch (IOException | RuntimeException exception) {
            return new SpeechBackendStatus(false,
                    "Google Cloud application credentials are unavailable on the server.");
        }
    }

    @Override
    public CloudSpeechResult recognize(
            String projectId,
            byte[] linearPcmAudio,
            int sampleRateHertz,
            String primaryLocale,
            List<String> alternativeLocales) {
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(sampleRateHertz)
                .setLanguageCode(primaryLocale)
                .addAllAlternativeLanguageCodes(alternativeLocales)
                .setModel("command_and_search")
                .setEnableAutomaticPunctuation(true)
                .build();
        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(linearPcmAudio)).build();
        try (SpeechClient client = createClient()) {
            RecognizeResponse response = client.recognize(config, audio);
            return result(response, primaryLocale);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Google Cloud speech credentials are unavailable.", exception);
        }
    }

    private SpeechClient createClient() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
                .getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
        credentials.refreshIfExpired();
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(
                        FixedCredentialsProvider.create(credentials))
                .build();
        return SpeechClient.create(settings);
    }

    private static CloudSpeechResult result(
            RecognizeResponse response, String fallbackLocale) {
        List<String> transcripts = new ArrayList<>();
        double confidenceTotal = 0;
        int confidenceCount = 0;
        String locale = fallbackLocale;
        for (SpeechRecognitionResult result : response.getResultsList()) {
            if (!result.getLanguageCode().isBlank()) {
                locale = result.getLanguageCode();
            }
            if (result.getAlternativesCount() == 0) continue;
            SpeechRecognitionAlternative alternative =
                    result.getAlternatives(0);
            if (!alternative.getTranscript().isBlank()) {
                transcripts.add(alternative.getTranscript().strip());
            }
            if (alternative.getConfidence() > 0) {
                confidenceTotal += alternative.getConfidence();
                confidenceCount++;
            }
        }
        String transcript = String.join(" ", transcripts).strip();
        if (transcript.isBlank()) {
            throw new IllegalStateException(
                    "No speech could be recognized from the recording.");
        }
        double confidence = confidenceCount == 0
                ? 0 : confidenceTotal / confidenceCount;
        return new CloudSpeechResult(transcript, confidence, locale);
    }
}
