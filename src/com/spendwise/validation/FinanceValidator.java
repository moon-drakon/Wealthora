package com.spendwise.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

public final class FinanceValidator {

    public static final int MAX_IDENTIFIER_LENGTH = 100;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_NOTE_LENGTH = 300;
    public static final BigDecimal MAX_AMOUNT =
            new BigDecimal("999999999.99");
    private static final int MONEY_SCALE = 2;
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

    private FinanceValidator() {
    }

    public static String validateIdentifier(
            String identifier, String entityName, String requiredPrefix) {
        String fieldName = Objects.requireNonNull(
                entityName, "Entity name is required.") + " ID";
        if (identifier == null || identifier.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        if (!identifier.equals(identifier.strip())) {
            throw new ValidationException(
                    fieldName + " cannot contain surrounding whitespace.");
        }
        if (identifier.length() > MAX_IDENTIFIER_LENGTH
                || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ValidationException(
                    fieldName + " must contain only uppercase letters, numbers, "
                    + "underscores, or hyphens and must not exceed "
                    + MAX_IDENTIFIER_LENGTH + " characters.");
        }
        if (requiredPrefix != null && !identifier.startsWith(requiredPrefix)) {
            throw new ValidationException(
                    fieldName + " must start with " + requiredPrefix + ".");
        }
        return identifier;
    }

    public static String validateRequiredText(
            String value, String fieldName, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new ValidationException(
                    fieldName + " must not exceed "
                    + maximumLength + " characters.");
        }
        rejectControlCharacters(normalized, fieldName);
        return normalized;
    }

    public static String validateOptionalText(
            String value, String fieldName, int maximumLength) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > maximumLength) {
            throw new ValidationException(
                    fieldName + " must not exceed "
                    + maximumLength + " characters.");
        }
        rejectControlCharactersExceptLineBreaks(normalized, fieldName);
        return normalized;
    }

    public static BigDecimal validatePositiveAmount(
            BigDecimal amount, String fieldName) {
        BigDecimal normalized = normalizeAmount(amount, fieldName);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    fieldName + " must be greater than zero.");
        }
        return normalized;
    }

    public static BigDecimal validateSignedAmount(
            BigDecimal amount, String fieldName) {
        return normalizeAmount(amount, fieldName);
    }

    public static LocalDate validatePostedDate(
            LocalDate date, String fieldName) {
        LocalDate requiredDate = Objects.requireNonNull(
                date, fieldName + " is required.");
        if (requiredDate.isAfter(LocalDate.now())) {
            throw new ValidationException(
                    fieldName + " cannot be in the future.");
        }
        return requiredDate;
    }

    private static BigDecimal normalizeAmount(
            BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new ValidationException(fieldName + " is required.");
        }
        if (amount.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException(
                    fieldName + " must be between -999999999.99 "
                    + "and 999999999.99.");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new ValidationException(
                    fieldName + " must have no more than two decimal places.");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static void rejectControlCharacters(
            String value, String fieldName) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new ValidationException(
                        fieldName + " cannot contain control characters.");
            }
        }
    }

    private static void rejectControlCharactersExceptLineBreaks(
            String value, String fieldName) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)
                    && character != '\n'
                    && character != '\r'
                    && character != '\t') {
                throw new ValidationException(
                        fieldName + " cannot contain unsupported control characters.");
            }
        }
    }
}
