package com.spendwise.validation;

import com.spendwise.model.Category;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class ExpenseValidator {

    private static final int MAX_ID_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 100;
    private static final int MAX_NOTES_LENGTH = 300;
    private static final int AMOUNT_SCALE = 2;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private ExpenseValidator() {
    }

    public static String validateId(String id) {
        return validateRequiredText(id, "Expense ID", MAX_ID_LENGTH);
    }

    public static String validateDescription(String description) {
        return validateRequiredText(description, "Description", MAX_DESCRIPTION_LENGTH);
    }

    public static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("Amount is required.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("Amount must not exceed 999999999.99.");
        }
        if (amount.stripTrailingZeros().scale() > AMOUNT_SCALE) {
            throw new ValidationException("Amount must have no more than two decimal places.");
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    public static LocalDate validateDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("Date is required.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("Date cannot be in the future.");
        }
        return date;
    }

    public static Category validateCategory(Category category) {
        if (category == null) {
            throw new ValidationException("Category is required.");
        }
        return category;
    }

    public static String validateNotes(String notes) {
        String normalizedNotes = notes == null ? "" : notes.trim();
        if (normalizedNotes.length() > MAX_NOTES_LENGTH) {
            throw new ValidationException("Notes must not exceed 300 characters.");
        }
        return normalizedNotes;
    }

    private static String validateRequiredText(String value, String fieldName, int maximumLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }

        String normalizedValue = value.trim();
        if (normalizedValue.length() > maximumLength) {
            throw new ValidationException(
                    fieldName + " must not exceed " + maximumLength + " characters.");
        }
        return normalizedValue;
    }
}
