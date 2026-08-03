package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

final class FinanceValidation {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");
    private static final Pattern EXTERNAL_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{2,119}");

    private FinanceValidation() {
    }

    static String externalId(String value, String prefix) {
        String normalized = requiredText(value, "Record identifier", 120);
        if (!normalized.startsWith(prefix)
                || !EXTERNAL_ID.matcher(normalized).matches()) {
            throw invalid("Record identifier is invalid.");
        }
        return normalized;
    }

    static String requiredText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is required.");
        }
        String normalized = value.strip();
        if (!normalized.equals(value) || normalized.length() > maximum) {
            throw invalid(field + " is invalid.");
        }
        rejectControls(normalized, field);
        return normalized;
    }

    static String optionalText(String value, String field, int maximum) {
        if (value == null || value.isEmpty()) return "";
        if (!value.equals(value.strip()) || value.length() > maximum) {
            throw invalid(field + " is invalid.");
        }
        rejectControls(value, field);
        return value;
    }

    static BigDecimal positiveAmount(BigDecimal value, String field) {
        BigDecimal amount = amount(value, field);
        if (amount.signum() <= 0) {
            throw invalid(field + " must be greater than zero.");
        }
        return amount;
    }

    static BigDecimal signedAmount(BigDecimal value, String field) {
        return amount(value, field);
    }

    static LocalDate postedDate(LocalDate date, String field) {
        if (date == null) throw invalid(field + " is required.");
        if (date.isAfter(LocalDate.now())) {
            throw invalid(field + " cannot be in the future.");
        }
        return date;
    }

    static String currency(String code) {
        String normalized = requiredText(code, "Currency code", 3)
                .toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw invalid("Currency code must be a valid ISO 4217 code.");
        }
    }

    static String color(String value) {
        String normalized = requiredText(value, "Account color", 7)
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("#[0-9A-F]{6}")) {
            throw invalid("Account color must use #RRGGBB format.");
        }
        return normalized;
    }

    static String enumValue(String value, String field, Set<String> allowed) {
        String normalized = requiredText(value, field, 40)
                .toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalid(field + " is invalid.");
        }
        return normalized;
    }

    static List<String> tags(List<String> supplied) {
        if (supplied == null || supplied.isEmpty()) return List.of();
        if (supplied.size() > 10) {
            throw invalid("A transaction can have at most 10 tags.");
        }
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String tag : supplied) {
            String normalized = requiredText(tag, "Tag", 30);
            if (normalized.contains("|")) {
                throw invalid("Tags cannot contain the | character.");
            }
            if (unique.add(normalized.toLowerCase(Locale.ROOT))) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static String encodeTags(List<String> tags) {
        return String.join("|", tags(tags));
    }

    static List<String> decodeTags(String value) {
        return value == null || value.isEmpty()
                ? List.of() : List.of(value.split("\\|", -1));
    }

    static String encodeLimits(Map<String, BigDecimal> limits) {
        if (limits == null || limits.isEmpty()) return "";
        StringBuilder encoded = new StringBuilder();
        limits.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (encoded.length() > 0) encoded.append(';');
                    encoded.append(externalId(entry.getKey(), ""))
                            .append('=')
                            .append(positiveAmount(entry.getValue(),
                                    "Category limit").toPlainString());
                });
        if (encoded.length() > 8000) {
            throw invalid("Category limits are too large.");
        }
        return encoded.toString();
    }

    static Map<String, BigDecimal> decodeLimits(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Map.of();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String item : encoded.split(";")) {
            int separator = item.indexOf('=');
            if (separator <= 0 || separator == item.length() - 1) {
                throw new IllegalStateException("Stored category limits are invalid.");
            }
            result.put(item.substring(0, separator),
                    new BigDecimal(item.substring(separator + 1)));
        }
        return Map.copyOf(result);
    }

    static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "FINANCE_VALIDATION_FAILED", message);
    }

    static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND,
                "FINANCE_NOT_FOUND", "Finance record was not found.");
    }

    static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT,
                "FINANCE_DUPLICATE", "A finance record already uses this identifier.");
    }

    private static BigDecimal amount(BigDecimal value, String field) {
        if (value == null) throw invalid(field + " is required.");
        if (value.abs().compareTo(MAX_AMOUNT) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw invalid(field + " must be a valid amount with at most two decimals.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void rejectControls(String value, String field) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)
                    && character != '\n' && character != '\r'
                    && character != '\t') {
                throw invalid(field + " contains unsupported characters.");
            }
        }
    }
}
