package com.spendwise.voice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public final class JavaSoundMicrophoneCapture implements MicrophoneCapture {

    public static final int SAMPLE_RATE_HERTZ = 16_000;
    private static final AudioFormat FORMAT = new AudioFormat(
            SAMPLE_RATE_HERTZ, 16, 1, true, false);
    private volatile String selectedIdentifier;
    private volatile TargetDataLine activeLine;
    private volatile boolean stopRequested;
    private volatile boolean cancelRequested;
    private volatile long captureStartedNanos;

    @Override
    public List<MicrophoneDevice> listMicrophones() {
        List<MicrophoneDevice> devices = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (int index = 0; index < mixers.length; index++) {
            Mixer mixer = AudioSystem.getMixer(mixers[index]);
            if (mixer.isLineSupported(lineInfo)) {
                devices.add(new MicrophoneDevice(Integer.toString(index),
                        mixers[index].getName()));
            }
        }
        if (selectedIdentifier == null && !devices.isEmpty()) {
            selectedIdentifier = devices.get(0).identifier();
        }
        return List.copyOf(devices);
    }

    @Override
    public void selectMicrophone(String identifier) {
        boolean found = listMicrophones().stream().anyMatch(
                device -> device.identifier().equals(identifier));
        if (!found) {
            throw new IllegalArgumentException(
                    "The selected microphone is unavailable.");
        }
        selectedIdentifier = identifier;
    }

    @Override
    public String getSelectedMicrophoneIdentifier() {
        return selectedIdentifier;
    }

    @Override
    public String getStatus() {
        List<MicrophoneDevice> devices = listMicrophones();
        return devices.isEmpty() ? "No compatible microphone was found."
                : "Microphone ready: " + devices.stream()
                        .filter(device -> device.identifier().equals(
                                selectedIdentifier))
                        .findFirst().orElse(devices.get(0)).displayName();
    }

    @Override
    public CapturedAudio capture(Duration maximumDuration) {
        if (maximumDuration == null || maximumDuration.isNegative()
                || maximumDuration.isZero()) {
            throw new IllegalArgumentException(
                    "A positive recording duration is required.");
        }
        TargetDataLine line = openSelectedLine();
        int maximumBytes = Math.toIntExact(Math.min(1_000_000L,
                maximumDuration.toMillis() * SAMPLE_RATE_HERTZ * 2 / 1000));
        byte[] recording = new byte[maximumBytes];
        int recordedBytes = 0;
        byte[] buffer = new byte[4_096];
        stopRequested = false;
        cancelRequested = false;
        captureStartedNanos = System.nanoTime();
        activeLine = line;
        try {
            try {
                line.start();
                while (!stopRequested && !cancelRequested
                        && !Thread.currentThread().isInterrupted()
                        && recordedBytes < maximumBytes) {
                    int available = Math.min(line.available(), Math.min(
                            buffer.length, maximumBytes - recordedBytes));
                    if (available > 0) {
                        int read = line.read(
                                buffer, 0, available - (available & 1));
                        if (read > 0) {
                            System.arraycopy(buffer, 0, recording,
                                    recordedBytes, read);
                            recordedBytes += read;
                        }
                    } else {
                        sleepBriefly();
                    }
                }
            } finally {
                line.stop();
                line.close();
                activeLine = null;
                java.util.Arrays.fill(buffer, (byte) 0);
            }
            if (cancelRequested) {
                throw new CancellationException(
                        "Voice recording was cancelled.");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException(
                        "Voice recording was interrupted.");
            }
            byte[] bytes = java.util.Arrays.copyOf(recording, recordedBytes);
            Duration duration = Duration.ofMillis(
                    Math.round(bytes.length * 1000.0
                            / (SAMPLE_RATE_HERTZ * 2)));
            return new CapturedAudio(bytes, duration);
        } finally {
            java.util.Arrays.fill(recording, (byte) 0);
        }
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    @Override
    public void cancel() {
        cancelRequested = true;
        TargetDataLine line = activeLine;
        if (line != null) line.close();
    }

    @Override
    public Duration getRecordingDuration() {
        if (activeLine == null || captureStartedNanos == 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(System.nanoTime() - captureStartedNanos);
    }

    @Override
    public boolean testMicrophone() {
        TargetDataLine line = openSelectedLine();
        try {
            line.start();
            return line.isOpen();
        } finally {
            line.stop();
            line.close();
        }
    }

    private TargetDataLine openSelectedLine() {
        List<MicrophoneDevice> devices = listMicrophones();
        if (devices.isEmpty()) {
            throw new IllegalStateException(
                    "No compatible microphone was found.");
        }
        String identifier = selectedIdentifier == null
                ? devices.get(0).identifier() : selectedIdentifier;
        int mixerIndex;
        try {
            mixerIndex = Integer.parseInt(identifier);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "The selected microphone is unavailable.", exception);
        }
        try {
            Mixer mixer = AudioSystem.getMixer(
                    AudioSystem.getMixerInfo()[mixerIndex]);
            TargetDataLine line = (TargetDataLine) mixer.getLine(
                    new DataLine.Info(TargetDataLine.class, FORMAT));
            line.open(FORMAT);
            return line;
        } catch (LineUnavailableException | IllegalArgumentException
                | ArrayIndexOutOfBoundsException exception) {
            throw new IllegalStateException(
                    "The selected microphone could not be opened.", exception);
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(15);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException(
                    "Voice recording was interrupted.");
        }
    }
}
