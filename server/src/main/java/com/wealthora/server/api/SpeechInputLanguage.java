package com.wealthora.server.api;

import java.util.List;

public enum SpeechInputLanguage {
    ENGLISH("en-US", List.of()),
    BANGLA("bn-BD", List.of()),
    BANGLISH_MIXED("en-US", List.of("bn-BD")),
    AUTOMATIC("en-US", List.of("bn-BD"));

    private final String primaryLocale;
    private final List<String> alternativeLocales;

    SpeechInputLanguage(
            String primaryLocale, List<String> alternativeLocales) {
        this.primaryLocale = primaryLocale;
        this.alternativeLocales = alternativeLocales;
    }

    public String primaryLocale() { return primaryLocale; }
    public List<String> alternativeLocales() { return alternativeLocales; }
}
