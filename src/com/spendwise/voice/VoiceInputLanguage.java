package com.spendwise.voice;

public enum VoiceInputLanguage {
    ENGLISH("English"),
    BANGLA("বাংলা"),
    BANGLISH_MIXED("Banglish / Mixed"),
    AUTOMATIC("Auto");

    private final String displayName;

    VoiceInputLanguage(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
