package com.spendwise.voice;

import com.spendwise.auth.UserSession;
import com.spendwise.auth.registration.HttpRegistrationGateway;
import com.spendwise.auth.registration.ServerConfiguration;
import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * Opt-in Windows live test for ADC, Speech V1, and real microphone capture.
 * Captured audio, passwords, verification codes, and tokens stay in memory.
 * The synthetic playback wave is wiped and deleted after each attempt.
 */
public final class LiveSpeechRecognitionTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String EMAIL_PREFIX = "wealthora.swing.e2e.";
    private static final String ENGLISH_COMMAND =
            "Add expense five hundred taka for food from cash today";

    private LiveSpeechRecognitionTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                        .contains("windows"),
                "The automated speaker-to-microphone test requires Windows.");

        Path repositoryRoot = requiredDirectory("WEALTHORA_REPOSITORY_ROOT");
        Path mailDirectory = requiredExternalDirectory(
                "WEALTHORA_DEV_MAIL_DIR", repositoryRoot);
        Path fixtureFile = requiredExternalPath(
                "WEALTHORA_LIVE_FIXTURE_FILE", repositoryRoot);
        char[] password = randomPassword();
        String marker = UUID.randomUUID().toString().replace("-", "");
        String email = EMAIL_PREFIX + marker + "@northsouth.edu";
        Path verificationFile = verificationFile(mailDirectory, email);
        HttpRegistrationGateway gateway = new HttpRegistrationGateway(
                ServerConfiguration.fromEnvironment());

        try {
            Files.writeString(fixtureFile, email + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            gateway.register("Wealthora Speech Test Student", email,
                    marker.substring(0, 12), password, password, true);
            String code = secretLine(awaitFile(verificationFile), "code=");
            gateway.verifyEmail(email, code);
            UserSession session = gateway.signIn(email, password);
            require(session.getUser().getEmail().equals(email),
                    "The synthetic CLOUD speech session was not created.");

            JavaSoundMicrophoneCapture microphone =
                    new JavaSoundMicrophoneCapture();
            AuthenticatedSpeechRecognitionProvider provider =
                    new AuthenticatedSpeechRecognitionProvider(
                            gateway, microphone);
            provider.refreshStatus();
            require(provider.isConfigured(),
                    "The production Speech V1 provider is unavailable.");
            List<MicrophoneDevice> devices = provider.listMicrophones();
            require(!devices.isEmpty(),
                    "No compatible microphone is available.");
            MicrophoneDevice selectedDevice = devices.stream()
                    .filter(LiveSpeechRecognitionTest::isLoopbackDevice)
                    .findFirst().orElse(devices.get(0));
            provider.selectMicrophone(selectedDevice.identifier());
            require(provider.testMicrophone(),
                    "The selected microphone could not be opened.");
            System.out.println("LiveSpeechProviderReady: PASS");
            System.out.println("LiveMicrophoneReady: PASS");

            VoiceTransactionParser parser = new VoiceTransactionParser(
                    List.of(Account.DEFAULT), List.of(Category.values()));
            System.out.println("LiveEnglishRecognition: RUNNING");
            verifyExpenseDraft("English", parser,
                    recognizeWithRetry(provider, VoiceInputLanguage.ENGLISH,
                            ENGLISH_COMMAND, selectedDevice.displayName()));
            System.out.println("LiveEnglishRecognition: PASS");
            System.out.println("LiveTranscriptParsed: PASS");
            System.out.println("LiveConfirmBeforeSave: PASS");
        } catch (Throwable failure) {
            System.err.println("Live speech test failed. Type="
                    + failure.getClass().getSimpleName());
            throw failure;
        } finally {
            try {
                gateway.logout();
            } catch (RuntimeException ignored) {
                // Server-side scoped cleanup still removes the fixture.
            }
            clearAndDelete(verificationFile);
            Arrays.fill(password, '\0');
        }
    }

    private static SpeechRecognitionResult recognizeSpokenCommand(
            AuthenticatedSpeechRecognitionProvider provider,
            VoiceInputLanguage language, String command,
            String captureDeviceName) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SpeechRecognitionResult> recognition = executor.submit(
                    () -> provider.recognize(language));
            awaitRecording(provider);
            Thread.sleep(700);
            speakThroughMatchingOutput(command, captureDeviceName);
            Thread.sleep(1_000);
            provider.stop();
            return recognition.get(45, TimeUnit.SECONDS);
        } finally {
            provider.cancel();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static SpeechRecognitionResult recognizeWithRetry(
            AuthenticatedSpeechRecognitionProvider provider,
            VoiceInputLanguage language, String command,
            String captureDeviceName) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return recognizeSpokenCommand(provider, language, command,
                        captureDeviceName);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw failure;
            } catch (Exception failure) {
                lastFailure = failure;
                if (attempt < 3) Thread.sleep(700);
            }
        }
        if (lastFailure == null) {
            throw new IllegalStateException(
                    "No speech recognition attempt was completed.");
        }
        throw lastFailure;
    }

    private static void verifyExpenseDraft(String label,
            VoiceTransactionParser parser, SpeechRecognitionResult result) {
        requireDraft(label, "transcript", !result.transcript().isBlank(),
                "Speech V1 returned an empty transcript.");
        VoiceTransactionDraft draft = parser.parse(result.transcript()).draft();
        requireDraft(label, "type",
                draft.getTransactionType() == TransactionType.EXPENSE,
                "The live transcript did not parse as an expense.");
        requireDraft(label, "amount", draft.getAmount() != null
                        && draft.getAmount().signum() > 0,
                "The live transcript did not contain a positive amount.");
        requireDraft(label, "account", draft.getSourceAccount() != null,
                "The live transcript did not resolve the Cash account.");
        requireDraft(label, "category",
                draft.getEffectiveCategory() == Category.FOOD,
                "The live transcript did not resolve the Food category.");
        requireDraft(label, "complete", draft.isComplete(),
                "The live transcript did not produce a complete draft.");
    }

    private static void requireDraft(String label, String field,
            boolean condition, String message) {
        if (!condition) {
            System.err.println("LiveDraftFailure: " + label + "." + field);
            throw new AssertionError(message);
        }
    }

    private static void awaitRecording(
            AuthenticatedSpeechRecognitionProvider provider) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (provider.getRecordingDuration().isZero()
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        require(!provider.getRecordingDuration().isZero(),
                "Microphone recording did not start.");
    }

    private static void speakThroughMatchingOutput(String text,
            String captureDeviceName)
            throws Exception {
        Path waveFile = Files.createTempFile(
                "wealthora-live-synthetic-speech-", ".wav");
        try {
            synthesizeWave(text, waveFile);
            playWave(waveFile, captureDeviceName);
        } finally {
            clearAndDelete(waveFile);
        }
    }

    private static void synthesizeWave(String text, Path waveFile)
            throws Exception {
        String escaped = text.replace("'", "''");
        String escapedPath = waveFile.toString().replace("'", "''");
        String command = "Add-Type -AssemblyName System.Speech; "
                + "$s=[System.Speech.Synthesis.SpeechSynthesizer]::new(); "
                + "try {$s.Volume=100; $s.Rate=-1; "
                + "$s.SetOutputToWaveFile('" + escapedPath + "'); $s.Speak('"
                + escaped + "')} finally {$s.Dispose()}";
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile",
                "-NonInteractive", "-Command", command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        require(process.waitFor(20, TimeUnit.SECONDS),
                "Windows speech synthesis timed out.");
        require(process.exitValue() == 0,
                "Windows speech synthesis was unavailable.");
        require(Files.size(waveFile) > 44,
                "Windows speech synthesis returned an empty wave file.");
    }

    private static void playWave(Path waveFile, String captureDeviceName)
            throws Exception {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(
                waveFile.toFile())) {
            DataLine.Info lineInfo = new DataLine.Info(
                    SourceDataLine.class, audio.getFormat());
            Mixer mixer = matchingOutputMixer(lineInfo, captureDeviceName);
            SourceDataLine output = mixer == null
                    ? (SourceDataLine) AudioSystem.getLine(lineInfo)
                    : (SourceDataLine) mixer.getLine(lineInfo);
            byte[] buffer = new byte[4_096];
            try {
                output.open(audio.getFormat());
                output.start();
                int count;
                while ((count = audio.read(buffer)) >= 0) {
                    if (count > 0) output.write(buffer, 0, count);
                }
                output.drain();
            } finally {
                if (output.isOpen()) {
                    output.stop();
                    output.close();
                }
                Arrays.fill(buffer, (byte) 0);
            }
        }
    }

    private static Mixer matchingOutputMixer(DataLine.Info lineInfo,
            String captureDeviceName) {
        String captureName = captureDeviceName.toLowerCase(Locale.ROOT);
        Mixer best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (!mixer.isLineSupported(lineInfo)) continue;
            String outputName = mixerInfo.getName().toLowerCase(Locale.ROOT);
            int score = outputName.contains("primary sound") ? 1 : 2;
            if (outputName.contains("speaker")
                    || outputName.contains("headphone")) score += 10;
            for (String hardware : List.of("realtek", "intel", "nvidia",
                    "amd", "usb", "bluetooth")) {
                if (captureName.contains(hardware)
                        && outputName.contains(hardware)) score += 100;
            }
            if (score > bestScore) {
                best = mixer;
                bestScore = score;
            }
        }
        return best;
    }

    private static Path verificationFile(Path directory, String email) {
        return directory.resolve(email.replaceAll(
                "[^A-Za-z0-9._-]", "_") + ".txt");
    }

    private static boolean isLoopbackDevice(MicrophoneDevice device) {
        String name = device.displayName().toLowerCase(Locale.ROOT);
        return name.contains("stereo mix")
                || name.contains("what u hear")
                || name.contains("loopback");
    }

    private static Path awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.isRegularFile(path) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        require(Files.isRegularFile(path),
                "The development verification message was not created.");
        return path;
    }

    private static String secretLine(Path file, String prefix)
            throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).strip())
                .filter(value -> !value.isEmpty())
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "The verification message is invalid."));
    }

    private static char[] randomPassword() {
        char[] result = new char[24];
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ"
                + "abcdefghijkmnopqrstuvwxyz23456789";
        for (int index = 0; index < result.length; index++) {
            result[index] = alphabet.charAt(
                    RANDOM.nextInt(alphabet.length()));
        }
        result[0] = 'W';
        result[1] = '7';
        return result;
    }

    private static Path requiredDirectory(String name) {
        String value = required(name);
        Path path = Path.of(value).toAbsolutePath().normalize();
        require(Files.isDirectory(path), name + " must be a directory.");
        return path;
    }

    private static Path requiredExternalDirectory(
            String name, Path repositoryRoot) {
        Path path = Path.of(required(name)).toAbsolutePath().normalize();
        require(Files.isDirectory(path), name + " must be a directory.");
        require(!path.startsWith(repositoryRoot),
                name + " must remain outside the repository.");
        return path;
    }

    private static Path requiredExternalPath(
            String name, Path repositoryRoot) {
        Path path = Path.of(required(name)).toAbsolutePath().normalize();
        require(!path.startsWith(repositoryRoot),
                name + " must remain outside the repository.");
        return path;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(), name + " is required.");
        return value.strip();
    }

    private static void clearAndDelete(Path path) {
        if (path == null || !Files.isRegularFile(path)) return;
        try {
            byte[] bytes = Files.readAllBytes(path);
            Arrays.fill(bytes, (byte) 0);
            Files.write(path, bytes, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The server-side cleanup repeats removal of fixture mail.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
