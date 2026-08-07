package com.spendwise.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Opt-in Windows integration check; no microphone or network is required. */
public final class WindowsOfflineSpeechRecognitionLiveTest {

    private WindowsOfflineSpeechRecognitionLiveTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                        .contains("windows"),
                "Windows offline speech integration requires Windows.");
        Path wave = Files.createTempFile(
                "wealthora-offline-speech-", ".wav");
        byte[] pcm = null;
        try {
            synthesize("Add expense five hundred taka for food today", wave);
            pcm = readPcm(wave);
            WindowsOfflineSpeechRecognitionProvider provider =
                    new WindowsOfflineSpeechRecognitionProvider(
                            new MemoryMicrophone(pcm));
            provider.refreshStatus();
            require(provider.isConfigured(), provider.getStatus());
            SpeechRecognitionResult result = provider.recognize(
                    VoiceInputLanguage.ENGLISH);
            require(!result.transcript().isBlank(),
                    "Windows returned an empty transcript.");
            System.out.println("WindowsOfflineProviderReady: PASS");
            System.out.println("WindowsOfflineRecognition: PASS");
            System.out.println("Transcript: " + result.transcript());
        } finally {
            if (pcm != null) Arrays.fill(pcm, (byte) 0);
            if (Files.isRegularFile(wave)) {
                byte[] bytes = Files.readAllBytes(wave);
                Arrays.fill(bytes, (byte) 0);
                Files.write(wave, bytes);
                Files.deleteIfExists(wave);
            }
        }
    }

    private static void synthesize(String text, Path wave) throws Exception {
        String script = "Add-Type -AssemblyName System.Speech; "
                + "$s=[System.Speech.Synthesis.SpeechSynthesizer]::new(); "
                + "try {$s.SetOutputToWaveFile('"
                + wave.toString().replace("'", "''") + "'); $s.Speak('"
                + text.replace("'", "''") + "')} finally {$s.Dispose()}";
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile",
                "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start();
        require(process.waitFor(20, TimeUnit.SECONDS),
                "Windows speech synthesis timed out.");
        require(process.exitValue() == 0 && Files.size(wave) > 44,
                "Windows speech synthesis is unavailable.");
    }

    private static byte[] readPcm(Path wave) throws Exception {
        AudioFormat target = new AudioFormat(
                JavaSoundMicrophoneCapture.SAMPLE_RATE_HERTZ,
                16, 1, true, false);
        try (AudioInputStream source = AudioSystem.getAudioInputStream(
                wave.toFile());
                AudioInputStream converted = AudioSystem.getAudioInputStream(
                        target, source)) {
            return converted.readAllBytes();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MemoryMicrophone implements MicrophoneCapture {
        private final byte[] pcm;

        private MemoryMicrophone(byte[] pcm) {
            this.pcm = pcm.clone();
        }

        @Override public List<MicrophoneDevice> listMicrophones() {
            return List.of(new MicrophoneDevice("memory", "Memory microphone"));
        }
        @Override public void selectMicrophone(String identifier) { }
        @Override public String getSelectedMicrophoneIdentifier() {
            return "memory";
        }
        @Override public String getStatus() { return "Memory microphone ready."; }
        @Override public CapturedAudio capture(Duration maximumDuration) {
            return new CapturedAudio(pcm.clone(), Duration.ofSeconds(3));
        }
        @Override public void stop() { }
        @Override public void cancel() { }
        @Override public Duration getRecordingDuration() {
            return Duration.ZERO;
        }
        @Override public boolean testMicrophone() { return true; }
    }
}
