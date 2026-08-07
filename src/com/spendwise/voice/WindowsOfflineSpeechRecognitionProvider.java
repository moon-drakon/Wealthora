package com.spendwise.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Uses the Windows desktop speech recognizer without uploading audio. */
public final class WindowsOfflineSpeechRecognitionProvider
        implements SpeechRecognitionProvider {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(20);
    private final MicrophoneCapture microphone;
    private volatile SpeechProviderStatus providerStatus =
            SpeechProviderStatus.NOT_CONFIGURED;
    private volatile String status =
            "Open Voice Quick Entry to check Windows offline speech.";
    private volatile Set<String> installedCultures = Set.of();
    private volatile Process activeProcess;

    public WindowsOfflineSpeechRecognitionProvider(
            MicrophoneCapture microphone) {
        this.microphone = Objects.requireNonNull(microphone);
    }

    @Override
    public String getDisplayName() {
        return "Windows Offline Speech";
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getMicrophoneStatus() {
        return microphone.getStatus();
    }

    @Override
    public SpeechProviderStatus getProviderStatus() {
        if (microphone.listMicrophones().isEmpty()) {
            return SpeechProviderStatus.UNAVAILABLE;
        }
        return providerStatus;
    }

    @Override
    public boolean isConfigured() {
        return getProviderStatus() == SpeechProviderStatus.READY;
    }

    @Override
    public synchronized void refreshStatus() {
        if (!isWindows()) {
            installedCultures = Set.of();
            providerStatus = SpeechProviderStatus.UNAVAILABLE;
            status = "Windows offline speech is available only on Windows.";
            return;
        }
        ProcessResult result = runPowerShell("""
                $ErrorActionPreference = 'Stop'
                Add-Type -AssemblyName System.Speech
                [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers() |
                    ForEach-Object { [Console]::Out.WriteLine('WEALTHORA_CULTURE=' + $_.Culture.Name) }
                """, new byte[0], Duration.ofSeconds(10));
        if (result.exitCode() != 0) {
            installedCultures = Set.of();
            providerStatus = SpeechProviderStatus.UNAVAILABLE;
            status = "Windows speech recognition is not installed.";
            return;
        }
        Set<String> cultures = new LinkedHashSet<>();
        result.output().lines().map(String::strip)
                .filter(value -> value.startsWith("WEALTHORA_CULTURE="))
                .map(value -> value.substring("WEALTHORA_CULTURE=".length()))
                .filter(value -> !value.isEmpty()).forEach(cultures::add);
        installedCultures = Set.copyOf(cultures);
        if (cultures.isEmpty()) {
            providerStatus = SpeechProviderStatus.UNAVAILABLE;
            status = "No Windows speech-recognition language is installed.";
        } else if (microphone.listMicrophones().isEmpty()) {
            providerStatus = SpeechProviderStatus.UNAVAILABLE;
            status = "Windows speech is installed, but no microphone is available.";
        } else {
            providerStatus = SpeechProviderStatus.READY;
            status = "Ready offline · installed language"
                    + (cultures.size() == 1 ? ": " : "s: ")
                    + String.join(", ", cultures);
        }
    }

    @Override
    public SpeechRecognitionResult recognize(
            SpeechRecognitionRequest request) {
        Objects.requireNonNull(request, "Speech request is required.");
        if (!isConfigured()) {
            throw new IllegalStateException(status);
        }
        String culture = cultureFor(request);
        if (!installedCultures.contains(culture)) {
            throw new IllegalStateException("Windows speech language "
                    + culture + " is not installed. Choose English or install "
                    + "the matching Windows speech language.");
        }
        CapturedAudio captured = microphone.capture(request.timeout());
        byte[] pcm = captured.linearPcm();
        byte[] wave = null;
        try {
            if (pcm.length < 3_200) {
                throw new IllegalStateException(
                        "The recording was too short to recognize.");
            }
            wave = waveFile(pcm, JavaSoundMicrophoneCapture.SAMPLE_RATE_HERTZ);
            ProcessResult result = runPowerShell(recognitionScript(culture),
                    wave, PROCESS_TIMEOUT);
            if (result.exitCode() != 0) {
                throw new IllegalStateException(
                        "Windows could not recognize clear speech. Try again in a quieter place or use manual entry.");
            }
            String encodedTranscript = result.output().lines()
                    .map(String::strip)
                    .filter(value -> value.startsWith("WEALTHORA_TEXT="))
                    .map(value -> value.substring("WEALTHORA_TEXT=".length()))
                    .findFirst().orElse("");
            String confidenceText = result.output().lines()
                    .map(String::strip)
                    .filter(value -> value.startsWith(
                            "WEALTHORA_CONFIDENCE="))
                    .map(value -> value.substring(
                            "WEALTHORA_CONFIDENCE=".length()))
                    .findFirst().orElse("");
            if (encodedTranscript.isEmpty() || confidenceText.isEmpty()) {
                throw new IllegalStateException(
                        "Windows did not return a speech transcript.");
            }
            String transcript = new String(Base64.getDecoder().decode(
                    encodedTranscript), StandardCharsets.UTF_8);
            double confidence = Math.max(0.0, Math.min(1.0,
                    Double.parseDouble(confidenceText)));
            VoiceInputLanguage detected = request.language()
                    == VoiceInputLanguage.AUTOMATIC
                    ? VoiceInputLanguage.ENGLISH : request.language();
            return new SpeechRecognitionResult(
                    transcript, confidence, detected);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Windows returned an invalid speech result.", exception);
        } finally {
            Arrays.fill(pcm, (byte) 0);
            if (wave != null) Arrays.fill(wave, (byte) 0);
        }
    }

    @Override
    public void stop() {
        microphone.stop();
    }

    @Override
    public void cancel() {
        microphone.cancel();
        Process process = activeProcess;
        if (process != null) process.destroyForcibly();
    }

    @Override
    public List<MicrophoneDevice> listMicrophones() {
        return microphone.listMicrophones();
    }

    @Override
    public void selectMicrophone(String identifier) {
        microphone.selectMicrophone(identifier);
    }

    @Override
    public String getSelectedMicrophoneIdentifier() {
        return microphone.getSelectedMicrophoneIdentifier();
    }

    @Override
    public Duration getRecordingDuration() {
        return microphone.getRecordingDuration();
    }

    @Override
    public boolean testMicrophone() {
        return microphone.testMicrophone();
    }

    static byte[] waveFile(byte[] pcm, int sampleRate) {
        ByteBuffer header = ByteBuffer.allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        byte[] wave = new byte[44 + pcm.length];
        System.arraycopy(header.array(), 0, wave, 0, 44);
        System.arraycopy(pcm, 0, wave, 44, pcm.length);
        return wave;
    }

    private String cultureFor(SpeechRecognitionRequest request) {
        return switch (request.language()) {
            case BANGLA -> "bn-BD";
            case ENGLISH, BANGLISH_MIXED, AUTOMATIC -> "en-US";
        };
    }

    private static String recognitionScript(String culture) {
        return """
                $ErrorActionPreference = 'Stop'
                Add-Type -AssemblyName System.Speech
                $memory = New-Object System.IO.MemoryStream
                [Console]::OpenStandardInput().CopyTo($memory)
                $memory.Position = 0
                $info = [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers() |
                    Where-Object { $_.Culture.Name -eq '%s' } |
                    Select-Object -First 1
                if ($null -eq $info) { exit 4 }
                $engine = New-Object System.Speech.Recognition.SpeechRecognitionEngine($info)
                try {
                    $grammar = New-Object System.Speech.Recognition.DictationGrammar
                    $engine.LoadGrammar($grammar)
                    $engine.SetInputToWaveStream($memory)
                    $result = $engine.Recognize()
                    if ($null -eq $result -or [String]::IsNullOrWhiteSpace($result.Text)) { exit 3 }
                    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($result.Text))
                    [Console]::Out.WriteLine('WEALTHORA_TEXT=' + $encoded)
                    [Console]::Out.WriteLine('WEALTHORA_CONFIDENCE=' + $result.Confidence.ToString([Globalization.CultureInfo]::InvariantCulture))
                } finally {
                    $engine.Dispose()
                    $memory.Dispose()
                }
                """.formatted(culture);
    }

    private synchronized ProcessResult runPowerShell(
            String script, byte[] input, Duration timeout) {
        String encodedScript = Base64.getEncoder().encodeToString(
                script.getBytes(StandardCharsets.UTF_16LE));
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encodedScript)
                    .redirectErrorStream(true).start();
            activeProcess = process;
            try (var output = process.getOutputStream()) {
                output.write(input);
            }
            boolean completed = process.waitFor(
                    timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "Windows speech recognition timed out.");
            }
            try (var output = new ByteArrayOutputStream()) {
                process.getInputStream().transferTo(output);
                return new ProcessResult(process.exitValue(),
                        output.toString(StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Windows PowerShell speech support could not be started.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Windows speech recognition was interrupted.", exception);
        } finally {
            if (activeProcess == process) activeProcess = null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .contains("windows");
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
