package com.spendwise.voice;

import java.time.Duration;
import java.util.List;

public interface MicrophoneCapture {

    List<MicrophoneDevice> listMicrophones();

    void selectMicrophone(String identifier);

    String getSelectedMicrophoneIdentifier();

    String getStatus();

    CapturedAudio capture(Duration maximumDuration);

    void stop();

    void cancel();

    Duration getRecordingDuration();

    boolean testMicrophone();
}
