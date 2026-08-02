package com.spendwise.voice;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceCommandNormalizer {

    private static final Map<String, String> PHRASES = phrases();

    public String normalize(String command) {
        String normalized = command == null ? "" : toEnglishDigits(
                Normalizer.normalize(command, Normalizer.Form.NFKC))
                .toLowerCase(Locale.ROOT)
                .replace("৳", " taka ")
                .replaceAll("[.!?;:।]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        for (Map.Entry<String, String> entry : PHRASES.entrySet()) {
            normalized = replacePhrase(
                    normalized, entry.getKey(), entry.getValue());
        }
        normalized = replaceNumberWords(normalized);
        return normalized.replaceAll("\\s+", " ").strip();
    }

    private static String replaceNumberWords(String value) {
        Map<String, Integer> numbers = new LinkedHashMap<>();
        numbers.putAll(Map.ofEntries(
                Map.entry("one", 1), Map.entry("two", 2),
                Map.entry("three", 3), Map.entry("four", 4),
                Map.entry("five", 5), Map.entry("six", 6),
                Map.entry("seven", 7), Map.entry("eight", 8),
                Map.entry("nine", 9), Map.entry("ten", 10),
                Map.entry("eleven", 11), Map.entry("twelve", 12),
                Map.entry("twenty", 20), Map.entry("thirty", 30),
                Map.entry("forty", 40), Map.entry("fifty", 50),
                Map.entry("sixty", 60), Map.entry("seventy", 70),
                Map.entry("eighty", 80), Map.entry("ninety", 90)));
        String result = value;
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            result = replacePhrase(result, entry.getKey() + " thousand",
                    Integer.toString(entry.getValue() * 1000));
            result = replacePhrase(result, entry.getKey() + " hundred",
                    Integer.toString(entry.getValue() * 100));
            result = result.replaceAll(
                    token(entry.getKey())
                    + "(?=\\s+(?:taka|bdt|usd|eur|gbp)(?![\\p{L}\\p{N}]))",
                    Integer.toString(entry.getValue()));
        }
        return result;
    }

    private static String toEnglishDigits(String value) {
        StringBuilder converted = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            converted.append(character >= '০' && character <= '৯'
                    ? (char) ('0' + character - '০') : character);
        }
        return converted.toString();
    }

    private static String replacePhrase(
            String value, String phrase, String replacement) {
        return Pattern.compile(token(phrase),
                Pattern.UNICODE_CHARACTER_CLASS)
                .matcher(value)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String token(String phrase) {
        return "(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase)
                + "(?![\\p{L}\\p{N}])";
    }

    private static Map<String, String> phrases() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("প্রতি মাসের", "every month");
        values.put("প্রতি মাসে", "every month");
        values.put("প্রতি সপ্তাহে", "every week");
        values.put("প্রতিদিন", "every day");
        values.put("প্রতি বছরে", "every year");
        values.put("নগদ টাকা", "cash");
        values.put("ব্যাংক অ্যাকাউন্ট", "bank account");
        values.put("খরচ করেছি", "expense");
        values.put("পেয়েছি", "income");
        values.put("পেয়েছি", "income");
        values.put("প্রাপ্ত", "income");
        values.put("স্থানান্তর", "transfer");
        values.put("পাঠালাম", "transfer");
        values.put("ট্রান্সফার", "transfer");
        values.put("বিকাশে", "bkash");
        values.put("বিকাশ", "bkash");
        values.put("নগদে", "nagad");
        values.put("নগদ", "nagad");
        values.put("রকেটে", "rocket");
        values.put("রকেট", "rocket");
        values.put("ব্যাংকে", "bank");
        values.put("ব্যাংক", "bank");
        values.put("ব্যাঙ্কে", "bank");
        values.put("ব্যাঙ্ক", "bank");
        values.put("ক্যাশ", "cash");
        values.put("খাবারে", "food");
        values.put("খাবারের", "food");
        values.put("খাবার", "food");
        values.put("বেতন", "salary");
        values.put("আয়", "income");
        values.put("আয়", "income");
        values.put("জমা", "income");
        values.put("খরচ", "expense");
        values.put("ব্যয়", "expense");
        values.put("ব্যয়", "expense");
        values.put("আজকে", "today");
        values.put("আজ", "today");
        values.put("গতকাল", "yesterday");
        values.put("কাল", "ambiguous-day");
        values.put("থেকে", "from");
        values.put("টাকা", "taka");
        values.put("তারিখে", "date");
        values.put("তারিখ", "date");
        values.put("ইন্টারনেট", "internet");
        values.put("বিল", "bill");
        values.put("হাজার", "thousand");
        values.put("শত", "hundred");
        values.put("একশ", "100");
        values.put("দুইশ", "200");
        values.put("তিনশ", "300");
        values.put("চারশ", "400");
        values.put("পাঁচশ", "500");
        values.put("ছয়শ", "600");
        values.put("ছয়শ", "600");
        values.put("সাতশ", "700");
        values.put("আটশ", "800");
        values.put("নয়শ", "900");
        values.put("নয়শ", "900");
        values.put("এক", "one");
        values.put("দুই", "two");
        values.put("তিন", "three");
        values.put("চার", "four");
        values.put("পাঁচ", "five");
        values.put("ছয়", "six");
        values.put("ছয়", "six");
        values.put("সাত", "seven");
        values.put("আট", "eight");
        values.put("নয়", "nine");
        values.put("নয়", "nine");
        values.put("দশ", "ten");
        values.put("বিশ", "twenty");
        values.put("ত্রিশ", "thirty");
        values.put("চল্লিশ", "forty");
        values.put("পঞ্চাশ", "fifty");
        values.put("proti mashe", "every month");
        values.put("proti masher", "every month");
        values.put("proti mash", "every month");
        values.put("proti shoptaho", "every week");
        values.put("protidin", "every day");
        values.put("proti bochor", "every year");
        values.put("bank e", "bank");
        values.put("food e", "food");
        values.put("paisi", "income");
        values.put("peyechi", "income");
        values.put("tarikh", "date");
        values.put("tarik", "date");
        values.put("aaj", "today");
        values.put("aj", "today");
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
