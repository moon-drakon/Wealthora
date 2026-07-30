package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;

public final class CategoryServiceTest {

    private static int passedTests;

    private CategoryServiceTest() {
    }

    public static void main(String[] args) {
        run("null repository", CategoryServiceTest::nullRepositoryIsRejected);
        run("built-in catalog", CategoryServiceTest::builtInsAppearFirst);
        run("empty custom catalog", CategoryServiceTest::emptyRepositoryAddsNoCustoms);
        run("unmodifiable catalog", CategoryServiceTest::catalogIsUnmodifiable);
        run("add category", CategoryServiceTest::addCreatesActiveCustomCategory);
        run("add normalization", CategoryServiceTest::addNormalizesName);
        run("stable generated ID", CategoryServiceTest::addUsesSuppliedStableIdentifier);
        run("rename", CategoryServiceTest::renameChangesOnlyDisplayName);
        run("archive", CategoryServiceTest::archiveChangesOnlyStatus);
        run("restore", CategoryServiceTest::restoreChangesOnlyStatus);
        run("active selection", CategoryServiceTest::archivedCategoriesAreNotSelectable);
        run("archived list visibility", CategoryServiceTest::archivedCategoriesRemainListed);
        run("duplicate built-in name", CategoryServiceTest::builtInNameDuplicateIsRejected);
        run("duplicate active name", CategoryServiceTest::activeNameDuplicateIsRejected);
        run("duplicate archived name", CategoryServiceTest::archivedNameDuplicateIsRejected);
        run("Unicode padded duplicate",
                CategoryServiceTest::unicodePaddedDuplicateIsRejected);
        run("invalid name", CategoryServiceTest::invalidNameDoesNotWrite);
        run("built-in protection", CategoryServiceTest::builtInMutationsAreRejected);
        run("unknown identifier", CategoryServiceTest::unknownIdentifierIsRejected);
        run("identifier normalization", CategoryServiceTest::identifierWhitespaceIsRejected);
        run("resolve built-in", CategoryServiceTest::resolveReturnsBuiltInSingleton);
        run("resolve custom", CategoryServiceTest::resolveReturnsCurrentCustomDefinition);
        run("repository failure", CategoryServiceTest::repositoryFailureIsNotHidden);
        run("failed validation safety", CategoryServiceTest::failedValidationPreservesRepository);
        System.out.println("All " + passedTests + " category service tests passed.");
    }

    private static void nullRepositoryIsRejected() {
        expectThrows(NullPointerException.class, () -> new CategoryService(null));
    }

    private static void builtInsAppearFirst() {
        CategoryService service = service(new InMemoryCategoryRepository());
        assertEquals(
                List.of(Category.values()),
                service.listAllCategories().subList(0, Category.values().length),
                "Built-in catalog order changed.");
    }

    private static void emptyRepositoryAddsNoCustoms() {
        assertEquals(Category.values().length,
                service(new InMemoryCategoryRepository()).listAllCategories().size(),
                "Empty custom repository changed the catalog size.");
    }

    private static void catalogIsUnmodifiable() {
        List<Category> categories =
                service(new InMemoryCategoryRepository()).listAllCategories();
        expectThrows(UnsupportedOperationException.class,
                () -> categories.add(custom("CUSTOM_EXTRA", "Extra", false)));
    }

    private static void addCreatesActiveCustomCategory() {
        InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
        Category added = service(repository).addCategory("Travel");
        assertTrue(added.isCustom(), "Added category was not custom.");
        assertTrue(added.isActive(), "Added category was not active.");
        assertEquals(added, repository.categories.get(0), "Added category was not stored.");
    }

    private static void addNormalizesName() {
        Category added = service(new InMemoryCategoryRepository())
                .addCategory("  Travel costs  ");
        assertEquals("Travel costs", added.getDisplayName(),
                "Added category name was not normalized.");
    }

    private static void addUsesSuppliedStableIdentifier() {
        Category added = service(new InMemoryCategoryRepository()).addCategory("Travel");
        assertEquals("CUSTOM_TEST_ID", added.getIdentifier(),
                "Service did not use its generated stable identifier.");
    }

    private static void renameChangesOnlyDisplayName() {
        InMemoryCategoryRepository repository = repositoryWith(
                custom("CUSTOM_001", "Travel", false));
        Category renamed = service(repository).renameCategory("CUSTOM_001", "Trips");
        assertEquals("CUSTOM_001", renamed.getIdentifier(), "Rename changed the ID.");
        assertEquals("Trips", renamed.getDisplayName(), "Rename did not change the name.");
        assertTrue(renamed.isActive(), "Rename changed status.");
    }

    private static void archiveChangesOnlyStatus() {
        InMemoryCategoryRepository repository = repositoryWith(
                custom("CUSTOM_001", "Travel", false));
        Category archived = service(repository).archiveCategory("CUSTOM_001");
        assertEquals("CUSTOM_001", archived.getIdentifier(), "Archive changed ID.");
        assertEquals("Travel", archived.getDisplayName(), "Archive changed name.");
        assertTrue(archived.isArchived(), "Archive did not change status.");
    }

    private static void restoreChangesOnlyStatus() {
        InMemoryCategoryRepository repository = repositoryWith(
                custom("CUSTOM_001", "Travel", true));
        Category restored = service(repository).restoreCategory("CUSTOM_001");
        assertEquals("CUSTOM_001", restored.getIdentifier(), "Restore changed ID.");
        assertEquals("Travel", restored.getDisplayName(), "Restore changed name.");
        assertTrue(restored.isActive(), "Restore did not activate category.");
    }

    private static void archivedCategoriesAreNotSelectable() {
        Category archived = custom("CUSTOM_001", "Travel", true);
        Category active = custom("CUSTOM_002", "Gifts", false);
        List<Category> selectable = service(repositoryWith(archived, active))
                .listSelectableCategories();
        assertFalse(selectable.contains(archived), "Archived category remained selectable.");
        assertTrue(selectable.contains(active), "Active category was not selectable.");
    }

    private static void archivedCategoriesRemainListed() {
        Category archived = custom("CUSTOM_001", "Travel", true);
        assertTrue(service(repositoryWith(archived)).listAllCategories().contains(archived),
                "Archived category disappeared from the full catalog.");
    }

    private static void builtInNameDuplicateIsRejected() {
        expectThrows(ValidationException.class,
                () -> service(new InMemoryCategoryRepository()).addCategory(" fOoD "));
    }

    private static void activeNameDuplicateIsRejected() {
        expectThrows(ValidationException.class, () ->
            service(repositoryWith(custom("CUSTOM_001", "Travel", false)))
                    .addCategory("TRAVEL"));
    }

    private static void archivedNameDuplicateIsRejected() {
        expectThrows(ValidationException.class, () ->
            service(repositoryWith(custom("CUSTOM_001", "Travel", true)))
                    .addCategory("travel"));
    }

    private static void unicodePaddedDuplicateIsRejected() {
        expectThrows(ValidationException.class, () ->
            service(repositoryWith(custom("CUSTOM_001", "Travel", false)))
                    .addCategory("\u2003tRaVeL\u2002"));
    }

    private static void invalidNameDoesNotWrite() {
        InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
        expectThrows(ValidationException.class,
                () -> service(repository).addCategory("\n"));
        assertEquals(0, repository.addCalls, "Invalid add reached the repository.");
    }

    private static void builtInMutationsAreRejected() {
        CategoryService service = service(new InMemoryCategoryRepository());
        expectThrows(ValidationException.class,
                () -> service.renameCategory("FOOD", "Meals"));
        expectThrows(ValidationException.class,
                () -> service.archiveCategory("OTHER"));
        expectThrows(ValidationException.class,
                () -> service.restoreCategory("BILLS"));
    }

    private static void unknownIdentifierIsRejected() {
        expectThrows(ValidationException.class, () ->
            service(new InMemoryCategoryRepository())
                    .resolveCategory("CUSTOM_UNKNOWN"));
    }

    private static void identifierWhitespaceIsRejected() {
        expectThrows(ValidationException.class, () ->
            service(new InMemoryCategoryRepository()).resolveCategory(" FOOD "));
    }

    private static void resolveReturnsBuiltInSingleton() {
        assertSame(
                Category.FOOD,
                service(new InMemoryCategoryRepository()).resolveCategory("FOOD"),
                "Built-in resolution did not preserve its singleton.");
    }

    private static void resolveReturnsCurrentCustomDefinition() {
        Category current = custom("CUSTOM_001", "Current name", true);
        assertSame(
                current,
                service(repositoryWith(current)).resolveCategory("CUSTOM_001"),
                "Custom resolution did not return the persisted definition.");
    }

    private static void repositoryFailureIsNotHidden() {
        InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
        repository.failReads = true;
        expectThrows(RepositoryException.class,
                () -> service(repository).listAllCategories());
    }

    private static void failedValidationPreservesRepository() {
        Category existing = custom("CUSTOM_001", "Travel", false);
        InMemoryCategoryRepository repository = repositoryWith(existing);
        expectThrows(ValidationException.class,
                () -> service(repository).renameCategory("CUSTOM_001", "Food"));
        assertSame(existing, repository.categories.get(0),
                "Failed rename changed the stored category.");
        assertEquals(0, repository.updateCalls,
                "Failed rename reached repository.update().");
    }

    private static CategoryService service(InMemoryCategoryRepository repository) {
        return new CategoryService(repository, () -> "CUSTOM_TEST_ID");
    }

    private static InMemoryCategoryRepository repositoryWith(Category... categories) {
        InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
        repository.categories.addAll(List.of(categories));
        return repository;
    }

    private static Category custom(String id, String name, boolean archived) {
        return Category.createCustom(id, name, archived);
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            passedTests++;
        } catch (RuntimeException | AssertionError exception) {
            throw new AssertionError("Category service test failed: " + name, exception);
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

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
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

    private static final class InMemoryCategoryRepository
            implements CategoryRepository {

        private final List<Category> categories = new ArrayList<>();
        private int addCalls;
        private int updateCalls;
        private boolean failReads;

        @Override
        public List<Category> findAll() {
            if (failReads) {
                throw new RepositoryException("Test-owned category read failure.");
            }
            return List.copyOf(categories);
        }

        @Override
        public void add(Category category) {
            addCalls++;
            categories.add(category);
        }

        @Override
        public void update(Category category) {
            updateCalls++;
            for (int index = 0; index < categories.size(); index++) {
                if (categories.get(index).equals(category)) {
                    categories.set(index, category);
                    return;
                }
            }
            throw new RepositoryException("Missing category.");
        }
    }
}
