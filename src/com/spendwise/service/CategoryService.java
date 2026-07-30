package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CategoryService {

    private final CategoryRepository repository;
    private final Supplier<String> identifierSupplier;

    public CategoryService(CategoryRepository repository) {
        this(repository, () -> "CUSTOM_"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
    }

    CategoryService(
            CategoryRepository repository, Supplier<String> identifierSupplier) {
        this.repository = Objects.requireNonNull(
                repository, "Category repository is required.");
        this.identifierSupplier = Objects.requireNonNull(
                identifierSupplier, "Category identifier supplier is required.");
    }

    public List<Category> listAllCategories() {
        List<Category> categories = new ArrayList<>(
                List.of(Category.values()));
        categories.addAll(repository.findAll());
        return List.copyOf(categories);
    }

    public List<Category> listSelectableCategories() {
        return listAllCategories().stream()
                .filter(Category::isActive)
                .toList();
    }

    public Category addCategory(String displayName) {
        String normalizedName = validateUniqueName(displayName, null);
        Category category = Category.createCustom(
                Objects.requireNonNull(
                        identifierSupplier.get(),
                        "Generated category identifier is required."),
                normalizedName,
                false);
        if (resolveOptional(category.getIdentifier()) != null) {
            throw new ValidationException(
                    "A generated category identifier already exists. Try again.");
        }
        repository.add(category);
        return category;
    }

    public Category renameCategory(String identifier, String displayName) {
        Category existing = requireCustom(identifier, "rename");
        String normalizedName = validateUniqueName(
                displayName, existing.getIdentifier());
        Category replacement = existing.withDisplayName(normalizedName);
        repository.update(replacement);
        return replacement;
    }

    public Category archiveCategory(String identifier) {
        Category existing = requireCustom(identifier, "archive");
        if (existing.isArchived()) {
            throw new ValidationException("Category is already archived.");
        }
        Category replacement = existing.withArchived(true);
        repository.update(replacement);
        return replacement;
    }

    public Category restoreCategory(String identifier) {
        Category existing = requireCustom(identifier, "restore");
        if (existing.isActive()) {
            throw new ValidationException("Category is already active.");
        }
        Category replacement = existing.withArchived(false);
        repository.update(replacement);
        return replacement;
    }

    public Category resolveCategory(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        Category category = resolveOptional(normalizedIdentifier);
        if (category == null) {
            throw new ValidationException(
                    "Unknown category identifier: " + normalizedIdentifier);
        }
        return category;
    }

    private String validateUniqueName(
            String displayName, String ignoredIdentifier) {
        Category candidate = Category.createCustom(
                "CUSTOM_VALIDATION", displayName, false);
        String normalizedName = candidate.getDisplayName();
        for (Category category : listAllCategories()) {
            if (!category.getIdentifier().equals(ignoredIdentifier)
                    && category.getDisplayName().equalsIgnoreCase(normalizedName)) {
                throw new ValidationException(
                        "A category named '" + normalizedName + "' already exists.");
            }
        }
        return normalizedName;
    }

    private Category requireCustom(String identifier, String operation) {
        Category category = resolveCategory(identifier);
        if (category.isBuiltIn()) {
            throw new ValidationException(
                    "Built-in categories cannot be " + operation + "d.");
        }
        return category;
    }

    private Category resolveOptional(String identifier) {
        if (Category.isBuiltInIdentifier(identifier)) {
            return Category.valueOf(identifier);
        }
        for (Category category : repository.findAll()) {
            if (category.getIdentifier().equals(identifier)) {
                return category;
            }
        }
        return null;
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("Category identifier is required.");
        }
        String normalizedIdentifier = identifier.trim();
        if (!normalizedIdentifier.equals(identifier)) {
            throw new ValidationException(
                    "Category identifier cannot contain surrounding whitespace.");
        }
        return normalizedIdentifier;
    }
}
