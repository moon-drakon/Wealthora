package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Category implements Comparable<Category> {

    public static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_IDENTIFIER_LENGTH = 100;
    private static final String CUSTOM_PREFIX = "CUSTOM_";
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

    public static final Category FOOD = builtIn("FOOD", "Food", 0);
    public static final Category TRANSPORT = builtIn("TRANSPORT", "Transport", 1);
    public static final Category SHOPPING = builtIn("SHOPPING", "Shopping", 2);
    public static final Category BILLS = builtIn("BILLS", "Bills", 3);
    public static final Category HEALTH = builtIn("HEALTH", "Health", 4);
    public static final Category EDUCATION = builtIn("EDUCATION", "Education", 5);
    public static final Category ENTERTAINMENT =
            builtIn("ENTERTAINMENT", "Entertainment", 6);
    public static final Category OTHER = builtIn("OTHER", "Other", 7);

    private static final Category[] BUILT_IN_VALUES = {
        FOOD, TRANSPORT, SHOPPING, BILLS, HEALTH, EDUCATION, ENTERTAINMENT, OTHER
    };

    private final String identifier;
    private final String displayName;
    private final boolean builtIn;
    private final boolean archived;
    private final int builtInOrdinal;

    private Category(
            String identifier,
            String displayName,
            boolean builtIn,
            boolean archived,
            int builtInOrdinal) {
        this.identifier = validateIdentifier(identifier, builtIn);
        this.displayName = validateDisplayName(displayName);
        this.builtIn = builtIn;
        this.archived = builtIn ? false : archived;
        this.builtInOrdinal = builtInOrdinal;
    }

    public static Category createCustom(
            String identifier, String displayName, boolean archived) {
        return new Category(identifier, displayName, false, archived, -1);
    }

    public static Category[] values() {
        return BUILT_IN_VALUES.clone();
    }

    public static Category valueOf(String identifier) {
        String requiredIdentifier = Objects.requireNonNull(
                identifier, "Category identifier is required.");
        return Arrays.stream(BUILT_IN_VALUES)
                .filter(category -> category.identifier.equals(requiredIdentifier))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown built-in category identifier: " + requiredIdentifier));
    }

    public static boolean isBuiltInIdentifier(String identifier) {
        if (identifier == null) {
            return false;
        }
        return Arrays.stream(BUILT_IN_VALUES)
                .anyMatch(category -> category.identifier.equals(identifier));
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * Retains the enum-style API used by legacy CSV code and existing callers.
     */
    public String name() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public boolean isCustom() {
        return !builtIn;
    }

    public boolean isActive() {
        return !archived;
    }

    public boolean isArchived() {
        return archived;
    }

    /**
     * Built-ins retain their historical enum ordering; custom categories return -1.
     */
    public int ordinal() {
        return builtInOrdinal;
    }

    public Category withDisplayName(String newDisplayName) {
        if (builtIn) {
            throw new ValidationException("Built-in categories cannot be renamed.");
        }
        return createCustom(identifier, newDisplayName, archived);
    }

    public Category withArchived(boolean newArchived) {
        if (builtIn) {
            throw new ValidationException("Built-in categories cannot be archived.");
        }
        return createCustom(identifier, displayName, newArchived);
    }

    @Override
    public int compareTo(Category other) {
        Objects.requireNonNull(other, "Category to compare is required.");
        if (identifier.equals(other.identifier)) {
            return 0;
        }
        if (builtIn && other.builtIn) {
            return Integer.compare(builtInOrdinal, other.builtInOrdinal);
        }
        if (builtIn != other.builtIn) {
            return builtIn ? -1 : 1;
        }
        int nameComparison = displayName.toLowerCase(Locale.ROOT)
                .compareTo(other.displayName.toLowerCase(Locale.ROOT));
        return nameComparison != 0
                ? nameComparison
                : identifier.compareTo(other.identifier);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Category category)) {
            return false;
        }
        return identifier.equals(category.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static Category builtIn(
            String identifier, String displayName, int ordinal) {
        return new Category(identifier, displayName, true, false, ordinal);
    }

    private static String validateIdentifier(String identifier, boolean builtIn) {
        if (identifier == null || identifier.isBlank()) {
            throw new ValidationException("Category identifier is required.");
        }
        if (!identifier.equals(identifier.trim())) {
            throw new ValidationException(
                    "Category identifier cannot contain surrounding whitespace.");
        }
        if (identifier.length() > MAX_IDENTIFIER_LENGTH
                || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ValidationException(
                    "Category identifier must contain only uppercase letters, "
                    + "numbers, underscores, or hyphens and must not exceed "
                    + MAX_IDENTIFIER_LENGTH + " characters.");
        }
        if (!builtIn && (!identifier.startsWith(CUSTOM_PREFIX)
                || isBuiltInIdentifier(identifier))) {
            throw new ValidationException(
                    "Custom category identifiers must start with " + CUSTOM_PREFIX + ".");
        }
        return identifier;
    }

    private static String validateDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new ValidationException("Category name is required.");
        }
        String normalizedName = displayName.trim();
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "Category name must not exceed " + MAX_NAME_LENGTH + " characters.");
        }
        for (int index = 0; index < normalizedName.length(); index++) {
            if (Character.isISOControl(normalizedName.charAt(index))) {
                throw new ValidationException(
                        "Category name cannot contain control characters.");
            }
        }
        return normalizedName;
    }
}
