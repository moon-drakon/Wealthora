package com.spendwise.voice;

import java.util.Objects;

/** Runtime voice preferences; no audio or transcript content is persisted. */
public final class VoiceEntrySettings {

    private boolean enabled = true;
    private VoiceInputLanguage preferredLanguage = VoiceInputLanguage.AUTOMATIC;
    private boolean doNotStoreAudio = true;

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized VoiceInputLanguage getPreferredLanguage() {
        return preferredLanguage;
    }

    public synchronized void setPreferredLanguage(
            VoiceInputLanguage preferredLanguage) {
        this.preferredLanguage = Objects.requireNonNull(preferredLanguage);
    }

    public synchronized boolean isDoNotStoreAudio() {
        return doNotStoreAudio;
    }

    public synchronized void setDoNotStoreAudio(boolean doNotStoreAudio) {
        if (!doNotStoreAudio) {
            throw new IllegalArgumentException(
                    "Wealthora does not store microphone audio.");
        }
        this.doNotStoreAudio = true;
    }
}
