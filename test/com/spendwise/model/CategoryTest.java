package com.spendwise.model;

import com.spendwise.validation.ValidationException;
import java.util.List;

public final class CategoryTest {

    private static int passedTests;

    private CategoryTest() {
    }

    public static void main(String[] args) {
        run("built-in identifiers", CategoryTest::builtInIdentifiersAreStable);
        run("built-in display names", CategoryTest::builtInDisplayNamesAreStable);
        run("built-in ordering", CategoryTest::builtInOrderingIsStable);
        run("values defensive copy", CategoryTest::valuesReturnsDefensiveCopy);
        run("legacy valueOf", CategoryTest::valueOfResolvesBuiltIns);
        run("custom definition", CategoryTest::customDefinitionIsImmutable);
        run("stable equality", CategoryTest::equalityUsesStableIdentifier);
        run("rename stable ID", CategoryTest::renamePreservesIdentifier);
        run("archive stable ID", CategoryTest::archivePreservesIdentifier);
        run("name trimming", CategoryTest::customNameIsTrimmed);
        run("blank name", CategoryTest::blankNameIsRejected);
        run("long name", CategoryTest::longNameIsRejected);
        run("control character", CategoryTest::controlCharacterIsRejected);
        run("invalid identifier", CategoryTest::invalidIdentifierIsRejected);
        run("non-custom identifier", CategoryTest::nonCustomIdentifierIsRejected);
        run("built-in rename protection", CategoryTest::builtInRenameIsRejected);
        run("built-in archive protection", CategoryTest::builtInArchiveIsRejected);
        run("Other protection", CategoryTest::otherRemainsProtectedAndActive);
        System.out.println("All " + passedTests + " category model tests passed.");
    }

    private static void builtInIdentifiersAreStable() {
        assertEquals(
                List.of(
                        "FOOD", "TRANSPORT", "SHOPPING", "BILLS",
                        "HEALTH", "EDUCATION", "ENTERTAINMENT", "OTHER"),
                List.of(Category.values()).stream().map(Category::name).toList(),
                "Built-in identifiers changed.");
    }

    private static void builtInDisplayNamesAreStable() {
        assertEquals(
                List.of(
                        "Food", "Transport", "Shopping", "Bills",
                        "Health", "Education", "Entertainment", "Other"),
                List.of(Category.values()).stream()
                        .map(Category::getDisplayName)
                        .toList(),
                "Built-in display names changed.");
    }

    private static void builtInOrderingIsStable() {
        for (int index = 0; index < Category.values().length; index++) {
            assertEquals(index, Category.values()[index].ordinal(),
                    "Built-in ordinal changed.");
        }
    }

    private static void valuesReturnsDefensiveCopy() {
        Category[] categories = Category.values();
        categories[0] = Category.OTHER;
        assertEquals(Category.FOOD, Category.values()[0],
                "Category.values() exposed mutable shared data.");
    }

    private static void valueOfResolvesBuiltIns() {
        assertSame(Category.EDUCATION, Category.valueOf("EDUCATION"),
                "Legacy valueOf did not return the built-in singleton.");
        expectThrows(IllegalArgumentException.class,
                () -> Category.valueOf("CUSTOM_UNKNOWN"));
    }

    private static void customDefinitionIsImmutable() {
        Category category = custom("Travel");
        assertEquals("CUSTOM_001", category.getIdentifier(), "Custom ID mismatch.");
        assertEquals("Travel", category.getDisplayName(), "Custom name mismatch.");
        assertTrue(category.isCustom(), "Custom classification mismatch.");
        assertTrue(category.isActive(), "New custom category should be active.");
        assertEquals(-1, category.ordinal(), "Custom categories need no enum ordinal.");
    }

    private static void equalityUsesStableIdentifier() {
        Category first = custom("First");
        Category second = Category.createCustom("CUSTOM_001", "Second", true);
        assertEquals(first, second, "Matching stable IDs should be equal.");
        assertEquals(first.hashCode(), second.hashCode(),
                "Matching stable IDs need matching hash codes.");
    }

    private static void renamePreservesIdentifier() {
        Category renamed = custom("Old").withDisplayName("New");
        assertEquals("CUSTOM_001", renamed.getIdentifier(),
                "Rename changed the stable identifier.");
        assertEquals("New", renamed.getDisplayName(), "Rename did not change the name.");
        assertTrue(renamed.isActive(), "Rename changed active status.");
    }

    private static void archivePreservesIdentifier() {
        Category archived = custom("Travel").withArchived(true);
        assertEquals("CUSTOM_001", archived.getIdentifier(),
                "Archive changed the stable identifier.");
        assertTrue(archived.isArchived(), "Archive status was not stored.");
        assertEquals("Travel", archived.getDisplayName(), "Archive changed the name.");
    }

    private static void customNameIsTrimmed() {
        assertEquals("Study supplies", custom("  Study supplies  ").getDisplayName(),
                "Surrounding whitespace was not normalized.");
    }

    private static void blankNameIsRejected() {
        expectThrows(ValidationException.class, () -> custom("   "));
        expectThrows(ValidationException.class, () ->
            Category.createCustom("CUSTOM_001", null, false));
    }

    private static void longNameIsRejected() {
        expectThrows(ValidationException.class, () ->
            custom("x".repeat(Category.MAX_NAME_LENGTH + 1)));
    }

    private static void controlCharacterIsRejected() {
        expectThrows(ValidationException.class, () -> custom("Food\nand drink"));
        expectThrows(ValidationException.class, () -> custom("Bad\u0007Name"));
    }

    private static void invalidIdentifierIsRejected() {
        expectThrows(ValidationException.class, () ->
            Category.createCustom("CUSTOM bad", "Name", false));
        expectThrows(ValidationException.class, () ->
            Category.createCustom("custom_001", "Name", false));
    }

    private static void nonCustomIdentifierIsRejected() {
        expectThrows(ValidationException.class, () ->
            Category.createCustom("TRAVEL", "Travel", false));
        expectThrows(ValidationException.class, () ->
            Category.createCustom("FOOD", "Travel", false));
    }

    private static void builtInRenameIsRejected() {
        expectThrows(ValidationException.class,
                () -> Category.FOOD.withDisplayName("Meals"));
    }

    private static void builtInArchiveIsRejected() {
        expectThrows(ValidationException.class,
                () -> Category.BILLS.withArchived(true));
    }

    private static void otherRemainsProtectedAndActive() {
        assertTrue(Category.OTHER.isBuiltIn(), "Other must remain built-in.");
        assertTrue(Category.OTHER.isActive(), "Other must remain active.");
        expectThrows(ValidationException.class,
                () -> Category.OTHER.withArchived(true));
    }

    private static Category custom(String name) {
        return Category.createCustom("CUSTOM_001", name, false);
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            passedTests++;
        } catch (RuntimeException | AssertionError exception) {
            throw new AssertionError("Category model test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, Runnable action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName() + " but received "
                    + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(
                "Expected " + expectedType.getSimpleName() + " to be thrown.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual);
        }
    }
}
