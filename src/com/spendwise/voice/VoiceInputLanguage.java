package com.spendwise.voice;

public enum VoiceInputLanguage {
    ENGLISH("English"),
    BANGLA("Bangla"),
    AUTOMATIC("Automatic");

    private final String displayName;

    VoiceInputLanguage(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
