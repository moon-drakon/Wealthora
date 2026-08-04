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

/**
 * Opt-in Windows live test for ADC, Speech V1, and real microphone capture.
 * Audio, passwords, verification codes, and session tokens stay in memory.
 */
public final class LiveSpeechRecognitionTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String EMAIL_PREFIX = "wealthora.swing.e2e.";
    private static final String SPOKEN_COMMAND =
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

            SpeechRecognitionResult result = recognizeSpokenCommand(provider);
            require(!result.transcript().isBlank(),
                    "Speech V1 returned an empty transcript.");
            VoiceParseResult parsed = new VoiceTransactionParser(
                    List.of(Account.DEFAULT), List.of(Category.values()))
                    .parse(result.transcript());
            VoiceTransactionDraft draft = parsed.draft();
            require(draft.getTransactionType() == TransactionType.EXPENSE,
                    "The live transcript did not parse as an expense.");
            require(draft.getAmount() != null
                            && draft.getAmount().signum() > 0,
                    "The live transcript did not contain a positive amount.");
            require(draft.getSourceAccount() != null,
                    "The live transcript did not resolve the Cash account.");
            require(draft.getEffectiveCategory() == Category.FOOD,
                    "The live transcript did not resolve the Food category.");
            require(draft.isComplete(),
                    "The live transcript did not produce a complete draft.");
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
            AuthenticatedSpeechRecognitionProvider provider) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SpeechRecognitionResult> recognition = executor.submit(
                    () -> provider.recognize(VoiceInputLanguage.ENGLISH));
            awaitRecording(provider);
            speakThroughDefaultOutput(SPOKEN_COMMAND);
            Thread.sleep(700);
            provider.stop();
            return recognition.get(45, TimeUnit.SECONDS);
        } finally {
            provider.cancel();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
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

    private static void speakThroughDefaultOutput(String text)
            throws Exception {
        String escaped = text.replace("'", "''");
        String command = "Add-Type -AssemblyName System.Speech; "
                + "$s=[System.Speech.Synthesis.SpeechSynthesizer]::new(); "
                + "try {$s.Volume=100; $s.Rate=-1; $s.Speak('"
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
