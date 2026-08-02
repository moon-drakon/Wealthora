package com.spendwise.voice;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VoiceCommandNormalizer {

    private static final Map<String, String> PHRASES = phrases();

    public String normalize(String command) {
        String normalized = command == null ? "" : Normalizer.normalize(
                command, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("৳", " taka ")
                .replaceAll("[.!?;:]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        for (Map.Entry<String, String> entry : PHRASES.entrySet()) {
            normalized = normalized.replaceAll(
                    "\\b" + entry.getKey() + "\\b", entry.getValue());
        }
        normalized = replaceNumberWords(normalized);
        return normalized.replaceAll("\\s+", " ").strip();
    }

    private static String replaceNumberWords(String value) {
        Map<String, Integer> numbers = Map.ofEntries(
                Map.entry("one", 1), Map.entry("two", 2),
                Map.entry("three", 3), Map.entry("four", 4),
                Map.entry("five", 5), Map.entry("six", 6),
                Map.entry("seven", 7), Map.entry("eight", 8),
                Map.entry("nine", 9), Map.entry("ten", 10));
        String result = value;
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            result = result.replaceAll(
                    "\\b" + entry.getKey() + " thousand\\b",
                    Integer.toString(entry.getValue() * 1000));
        }
        return result;
    }

    private static Map<String, String> phrases() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("proti mashe", "every month");
        values.put("proti mash", "every month");
        values.put("proti shoptaho", "every week");
        values.put("protidin", "every day");
        values.put("proti bochor", "every year");
        values.put("add koro", "add");
        values.put("jog koro", "add");
        values.put("khoroch", "expense");
        values.put("er jonno", "for");
        values.put("ajke", "today");
        values.put("aajke", "today");
        values.put("theke", "from");
        values.put("taka", "taka");
        return java.util.Collections.unmodifiableMap(values);
    }
}
