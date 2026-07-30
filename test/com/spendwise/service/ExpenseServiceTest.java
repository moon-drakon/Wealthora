package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ExpenseServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2020, 1, 10);
    private static int passedTests;

    public static void main(String[] args) {
        runTest("null repository rejection", ExpenseServiceTest::nullRepositoryIsRejected);
        runTest("create and store expense", ExpenseServiceTest::createExpenseStoresExpense);
        runTest("create valid UUID", ExpenseServiceTest::createExpenseGeneratesValidId);
        runTest("create normalized values", ExpenseServiceTest::createExpenseNormalizesValues);
        runTest("invalid create safety", ExpenseServiceTest::invalidCreationDoesNotChangeRepository);
        runTest("get all preserves order", ExpenseServiceTest::getAllExpensesPreservesOrder);
        runTest("get all is unmodifiable", ExpenseServiceTest::getAllExpensesIsUnmodifiable);
        runTest("find existing expense", ExpenseServiceTest::findExpenseByIdFindsExpense);
        runTest("find missing expense", ExpenseServiceTest::findExpenseByIdReturnsEmpty);
        runTest("update preserves ID", ExpenseServiceTest::updatePreservesId);
        runTest("update preserves position", ExpenseServiceTest::updatePreservesPosition);
        runTest("update changes editable fields", ExpenseServiceTest::updateChangesEditableFields);
        runTest("missing update exception", ExpenseServiceTest::missingUpdateThrowsNotFound);
        runTest("missing update safety", ExpenseServiceTest::missingUpdateDoesNotChangeRepository);
        runTest("invalid update safety", ExpenseServiceTest::invalidUpdateLeavesStoredExpenseUnchanged);
        runTest("replacement-object update", ExpenseServiceTest::updateUsesReplacementObject);
        runTest("successful delete", ExpenseServiceTest::successfulDeleteReturnsTrue);
        runTest("missing delete", ExpenseServiceTest::missingDeleteReturnsFalse);
        runTest("invalid ID validation", ExpenseServiceTest::invalidIdsAreRejectedConsistently);
        runTest("description search", ExpenseServiceTest::descriptionSearchIsCaseInsensitive);
        runTest("notes search", ExpenseServiceTest::notesSearchIsCaseInsensitive);
        runTest("category enum-name search", ExpenseServiceTest::categoryEnumNameSearchWorks);
        runTest("category display-name search", ExpenseServiceTest::categoryDisplayNameSearchWorks);
        runTest("unrestricted text search", ExpenseServiceTest::nullAndBlankSearchMatchAll);
        runTest("category filter", ExpenseServiceTest::categoryFilteringWorks);
        runTest("inclusive start date", ExpenseServiceTest::startDateIsInclusive);
        runTest("inclusive end date", ExpenseServiceTest::endDateIsInclusive);
        runTest("open date boundaries", ExpenseServiceTest::openEndedDateFilteringWorks);
        runTest("invalid date range", ExpenseServiceTest::invalidDateRangeIsRejected);
        runTest("combined search and filters", ExpenseServiceTest::searchAndFiltersWorkTogether);
        runTest("original sort order", ExpenseServiceTest::originalOrderIsPreserved);
        runTest("newest date sorting", ExpenseServiceTest::newestFirstDateSortingWorks);
        runTest("oldest date sorting", ExpenseServiceTest::oldestFirstDateSortingWorks);
        runTest("highest amount sorting", ExpenseServiceTest::highestFirstAmountSortingWorks);
        runTest("lowest amount sorting", ExpenseServiceTest::lowestFirstAmountSortingWorks);
        runTest("description ascending sorting", ExpenseServiceTest::descriptionAscendingWorks);
        runTest("description descending sorting", ExpenseServiceTest::descriptionDescendingWorks);
        runTest("stable equal-value sorting", ExpenseServiceTest::equalSortValuesRemainStable);
        runTest("repository order is unchanged", ExpenseServiceTest::sortingDoesNotChangeRepositoryOrder);
        runTest("find result is unmodifiable", ExpenseServiceTest::findExpensesResultIsUnmodifiable);
        runTest("null sort order", ExpenseServiceTest::nullSortOrderIsRejected);
        runTest("empty summary", ExpenseServiceTest::emptySummaryContainsZeros);
        runTest("summary count", ExpenseServiceTest::summaryCountsExpenses);
        runTest("summary exact total", ExpenseServiceTest::summaryCalculatesExactTotal);
        runTest("summary rounded average", ExpenseServiceTest::summaryAverageUsesHalfUp);
        runTest("summary includes categories", ExpenseServiceTest::summaryIncludesEveryCategory);
        runTest("summary category totals", ExpenseServiceTest::categoryTotalsAreCorrect);
        runTest("summary unused category", ExpenseServiceTest::unusedCategoryReturnsZero);
        runTest("summary map is unmodifiable", ExpenseServiceTest::categoryTotalsMapIsUnmodifiable);
        runTest("overall summary", ExpenseServiceTest::overallSummaryUsesRepositoryExpenses);
        runTest("filtered summary", ExpenseServiceTest::filteredResultCanBeSummarized);
        runTest("null summary list", ExpenseServiceTest::nullSummaryInputIsRejected);
        runTest("null summary element", ExpenseServiceTest::nullSummaryElementIsRejected);
        runTest("null summary category", ExpenseServiceTest::nullSummaryCategoryIsRejected);
        runTest("summary defensive copy", ExpenseServiceTest::summaryDefensivelyCopiesCategoryTotals);
        runTest("validation exception propagation", ExpenseServiceTest::validationExceptionIsNotHidden);
        runTest("repository exception propagation", ExpenseServiceTest::repositoryExceptionIsNotHidden);
        runTest("validation failure mutation safety", ExpenseServiceTest::validationFailuresDoNotChangeData);
        runTest("single search snapshot load", ExpenseServiceTest::findExpensesLoadsSnapshotOnce);
        runTest("Unicode search", ExpenseServiceTest::unicodeSearchIsPreserved);

        System.out.println("All " + passedTests + " service tests passed.");
    }

    private static void nullRepositoryIsRejected() {
        expectThrows(
                NullPointerException.class,
                () -> new ExpenseService(null),
                "A null repository should be rejected.");
    }

    private static void createExpenseStoresExpense() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        ExpenseService service = new ExpenseService(repository);

        Expense created = service.createExpense(
                "Lunch", new BigDecimal("12.50"), BASE_DATE, Category.FOOD, "Team meal");

        assertEquals(1, repository.size(), "Created expense was not stored.");
        assertSame(created, repository.findById(created.getId()).orElseThrow(),
                "Service should return the stored expense.");
    }

    private static void createExpenseGeneratesValidId() {
        Expense created = new ExpenseService(new InMemoryExpenseRepository()).createExpense(
                "Lunch", new BigDecimal("12.50"), BASE_DATE, Category.FOOD, "");

        UUID parsedId = UUID.fromString(created.getId());
        assertEquals(parsedId.toString(), created.getId(), "Created ID should be a UUID.");
    }

    private static void createExpenseNormalizesValues() {
        Expense created = new ExpenseService(new InMemoryExpenseRepository()).createExpense(
                "  Lunch  ",
                new BigDecimal("12.300"),
                BASE_DATE,
                Category.FOOD,
                "  Team meal  ");

        assertEquals("Lunch", created.getDescription(), "Description was not trimmed.");
        assertEquals(new BigDecimal("12.30"), created.getAmount(), "Amount was not normalized.");
        assertEquals("Team meal", created.getNotes(), "Notes were not trimmed.");
    }

    private static void invalidCreationDoesNotChangeRepository() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense existing = expense("existing", "Existing", "10.00", BASE_DATE, Category.OTHER, "");
        repository.add(existing);
        ExpenseService service = new ExpenseService(repository);

        expectThrows(
                ValidationException.class,
                () -> service.createExpense(
                        "  ", new BigDecimal("12.50"), BASE_DATE, Category.FOOD, ""),
                "Invalid creation should preserve model validation.");

        assertEquals(1, repository.size(), "Invalid creation changed repository size.");
        assertSame(existing, repository.findById("existing").orElseThrow(),
                "Invalid creation replaced existing data.");
    }

    private static void getAllExpensesPreservesOrder() {
        InMemoryExpenseRepository repository = searchRepository();

        assertIds(
                new ExpenseService(repository).getAllExpenses(),
                List.of("food", "transport", "entertainment", "education"),
                "Repository order was not preserved.");
    }

    private static void getAllExpensesIsUnmodifiable() {
        List<Expense> expenses =
                new ExpenseService(searchRepository()).getAllExpenses();

        expectThrows(
                UnsupportedOperationException.class,
                () -> expenses.add(simpleExpense("extra")),
                "All-expenses snapshot should be unmodifiable.");
    }

    private static void findExpenseByIdFindsExpense() {
        ExpenseService service = new ExpenseService(searchRepository());

        Expense found = service.findExpenseById("  transport  ").orElseThrow();

        assertEquals("Bus Pass", found.getDescription(), "Wrong expense was found.");
    }

    private static void findExpenseByIdReturnsEmpty() {
        Optional<Expense> result =
                new ExpenseService(searchRepository()).findExpenseById("missing");

        assertTrue(result.isEmpty(), "Missing ID should return Optional.empty().");
    }

    private static void updatePreservesId() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(simpleExpense("expense-001"));
        ExpenseService service = new ExpenseService(repository);

        Expense updated = service.updateExpense(
                "  expense-001  ",
                "Updated",
                new BigDecimal("20.00"),
                BASE_DATE.minusDays(1),
                Category.SHOPPING,
                "Changed");

        assertEquals("expense-001", updated.getId(), "Update changed the expense ID.");
    }

    private static void updatePreservesPosition() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(simpleExpense("first"));
        repository.add(simpleExpense("second"));
        repository.add(simpleExpense("third"));
        ExpenseService service = new ExpenseService(repository);

        service.updateExpense(
                "second",
                "Updated second",
                new BigDecimal("22.00"),
                BASE_DATE,
                Category.BILLS,
                "");

        assertIds(
                repository.findAll(),
                List.of("first", "second", "third"),
                "Update changed repository position.");
    }

    private static void updateChangesEditableFields() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(simpleExpense("expense-001"));
        ExpenseService service = new ExpenseService(repository);
        LocalDate updatedDate = BASE_DATE.minusDays(2);

        Expense updated = service.updateExpense(
                "expense-001",
                "  Reference book  ",
                new BigDecimal("45"),
                updatedDate,
                Category.EDUCATION,
                "  Second edition  ");

        assertEquals("Reference book", updated.getDescription(), "Description was not updated.");
        assertEquals(new BigDecimal("45.00"), updated.getAmount(), "Amount was not updated.");
        assertEquals(updatedDate, updated.getDate(), "Date was not updated.");
        assertEquals(Category.EDUCATION, updated.getCategory(), "Category was not updated.");
        assertEquals("Second edition", updated.getNotes(), "Notes were not updated.");
    }

    private static void missingUpdateThrowsNotFound() {
        ExpenseNotFoundException exception = expectThrows(
                ExpenseNotFoundException.class,
                () -> new ExpenseService(new InMemoryExpenseRepository()).updateExpense(
                        "missing",
                        "Lunch",
                        new BigDecimal("12.50"),
                        BASE_DATE,
                        Category.FOOD,
                        ""),
                "A missing update should report that the expense was not found.");

        assertTrue(exception.getMessage().contains("missing"),
                "Not-found message should identify the requested expense.");
    }

    private static void missingUpdateDoesNotChangeRepository() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense existing = simpleExpense("existing");
        repository.add(existing);
        ExpenseService service = new ExpenseService(repository);

        expectThrows(
                ExpenseNotFoundException.class,
                () -> service.updateExpense(
                        "missing",
                        "Changed",
                        new BigDecimal("20.00"),
                        BASE_DATE,
                        Category.BILLS,
                        ""),
                "Missing update should fail.");

        assertEquals(1, repository.size(), "Missing update changed repository size.");
        assertSame(existing, repository.findById("existing").orElseThrow(),
                "Missing update changed stored data.");
    }

    private static void invalidUpdateLeavesStoredExpenseUnchanged() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense existing = simpleExpense("existing");
        repository.add(existing);
        ExpenseService service = new ExpenseService(repository);

        expectThrows(
                ValidationException.class,
                () -> service.updateExpense(
                        "existing",
                        "",
                        new BigDecimal("20.00"),
                        BASE_DATE,
                        Category.BILLS,
                        "Changed"),
                "Invalid replacement should fail validation.");

        assertSame(existing, repository.findById("existing").orElseThrow(),
                "Invalid update replaced the stored expense.");
        assertEquals("Test expense", existing.getDescription(),
                "Invalid update mutated the stored expense.");
    }

    private static void updateUsesReplacementObject() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense original = simpleExpense("existing");
        repository.add(original);
        ExpenseService service = new ExpenseService(repository);

        Expense replacement = service.updateExpense(
                "existing",
                "Replacement",
                new BigDecimal("25.00"),
                BASE_DATE,
                Category.HEALTH,
                "New object");

        assertNotSame(original, replacement, "Update should create a replacement object.");
        assertSame(replacement, repository.findById("existing").orElseThrow(),
                "Repository should contain the replacement object.");
        assertEquals("Test expense", original.getDescription(),
                "Original repository-owned object was mutated.");
    }

    private static void successfulDeleteReturnsTrue() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(simpleExpense("existing"));
        ExpenseService service = new ExpenseService(repository);

        assertTrue(service.deleteExpense("existing"), "Existing expense should be deleted.");
        assertEquals(0, repository.size(), "Deleted expense remains in the repository.");
    }

    private static void missingDeleteReturnsFalse() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(simpleExpense("existing"));

        assertFalse(
                new ExpenseService(repository).deleteExpense("missing"),
                "Missing delete should return false.");
        assertEquals(1, repository.size(), "Missing delete changed repository data.");
    }

    private static void invalidIdsAreRejectedConsistently() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense existing = simpleExpense("existing");
        repository.add(existing);
        ExpenseService service = new ExpenseService(repository);

        expectThrows(
                ValidationException.class,
                () -> service.findExpenseById("   "),
                "Find should reject a blank ID.");
        expectThrows(
                ValidationException.class,
                () -> service.updateExpense(
                        null,
                        "Changed",
                        new BigDecimal("20.00"),
                        BASE_DATE,
                        Category.OTHER,
                        ""),
                "Update should reject a null ID.");
        expectThrows(
                ValidationException.class,
                () -> service.deleteExpense("i".repeat(101)),
                "Delete should reject an overlong ID.");
        assertSame(existing, repository.findById("existing").orElseThrow(),
                "Invalid IDs should not change repository data.");
    }

    private static void descriptionSearchIsCaseInsensitive() {
        List<Expense> matches = find(searchRepository(), "cOfFeE", null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(matches, List.of("food"), "Description search returned wrong records.");
    }

    private static void notesSearchIsCaseInsensitive() {
        List<Expense> matches = find(searchRepository(), "COMMUTE", null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(matches, List.of("transport"), "Notes search returned wrong records.");
    }

    private static void categoryEnumNameSearchWorks() {
        List<Expense> matches = find(searchRepository(), "ENTERTAINMENT", null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(matches, List.of("entertainment"), "Category enum-name search failed.");
    }

    private static void categoryDisplayNameSearchWorks() {
        List<Expense> matches = find(searchRepository(), "  Transport  ", null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(matches, List.of("transport"), "Category display-name search failed.");
    }

    private static void nullAndBlankSearchMatchAll() {
        InMemoryExpenseRepository repository = searchRepository();

        assertEquals(4, find(repository, null, null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER).size(), "Null search should match all.");
        assertEquals(4, find(repository, "   ", null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER).size(), "Blank search should match all.");
    }

    private static void categoryFilteringWorks() {
        List<Expense> matches = find(searchRepository(), null, Category.EDUCATION, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(matches, List.of("education"), "Category filter returned wrong records.");
    }

    private static void startDateIsInclusive() {
        List<Expense> matches = find(
                searchRepository(), null, null, BASE_DATE.plusDays(2), null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(
                matches,
                List.of("transport", "entertainment", "education"),
                "Start date should be inclusive.");
    }

    private static void endDateIsInclusive() {
        List<Expense> matches = find(
                searchRepository(), null, null, null, BASE_DATE.plusDays(2),
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(
                matches,
                List.of("food", "transport", "entertainment"),
                "End date should be inclusive.");
    }

    private static void openEndedDateFilteringWorks() {
        InMemoryExpenseRepository repository = searchRepository();

        assertIds(
                find(repository, null, null, BASE_DATE.plusDays(5), null,
                        ExpenseSortOrder.ORIGINAL_ORDER),
                List.of("education"),
                "Null end date should leave the upper boundary open.");
        assertIds(
                find(repository, null, null, null, BASE_DATE,
                        ExpenseSortOrder.ORIGINAL_ORDER),
                List.of("food"),
                "Null start date should leave the lower boundary open.");
    }

    private static void invalidDateRangeIsRejected() {
        InMemoryExpenseRepository repository = searchRepository();
        repository.resetFindAllCalls();

        expectThrows(
                ValidationException.class,
                () -> find(
                        repository,
                        null,
                        null,
                        BASE_DATE.plusDays(2),
                        BASE_DATE.plusDays(1),
                        ExpenseSortOrder.ORIGINAL_ORDER),
                "Start date after end date should be rejected.");

        assertEquals(0, repository.getFindAllCalls(),
                "Invalid range should fail before loading repository data.");
    }

    private static void searchAndFiltersWorkTogether() {
        List<Expense> matches = find(
                searchRepository(),
                "course",
                Category.EDUCATION,
                BASE_DATE.plusDays(5),
                BASE_DATE.plusDays(5),
                ExpenseSortOrder.ORIGINAL_ORDER);

        assertIds(matches, List.of("education"), "Combined restrictions returned wrong records.");
    }

    private static void originalOrderIsPreserved() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.ORIGINAL_ORDER),
                List.of("food", "transport", "entertainment", "education"),
                "Original sorting changed repository order.");
    }

    private static void newestFirstDateSortingWorks() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.DATE_NEWEST_FIRST),
                List.of("education", "transport", "entertainment", "food"),
                "Newest-first date order is incorrect.");
    }

    private static void oldestFirstDateSortingWorks() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.DATE_OLDEST_FIRST),
                List.of("food", "transport", "entertainment", "education"),
                "Oldest-first date order is incorrect.");
    }

    private static void highestFirstAmountSortingWorks() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.AMOUNT_HIGHEST_FIRST),
                List.of("transport", "education", "entertainment", "food"),
                "Highest-first amount order is incorrect.");
    }

    private static void lowestFirstAmountSortingWorks() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.AMOUNT_LOWEST_FIRST),
                List.of("food", "entertainment", "education", "transport"),
                "Lowest-first amount order is incorrect.");
    }

    private static void descriptionAscendingWorks() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.DESCRIPTION_A_TO_Z),
                List.of("education", "transport", "entertainment", "food"),
                "Description A-to-Z order is incorrect.");
    }

    private static void descriptionDescendingWorks() {
        assertIds(
                find(searchRepository(), null, null, null, null,
                        ExpenseSortOrder.DESCRIPTION_Z_TO_A),
                List.of("food", "entertainment", "transport", "education"),
                "Description Z-to-A order is incorrect.");
    }

    private static void equalSortValuesRemainStable() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(expense("first", "Same", "10.00", BASE_DATE, Category.OTHER, ""));
        repository.add(expense("second", "same", "10.00", BASE_DATE, Category.FOOD, ""));
        repository.add(expense("third", "SAME", "10.00", BASE_DATE, Category.BILLS, ""));

        assertIds(
                find(repository, null, null, null, null,
                        ExpenseSortOrder.DESCRIPTION_A_TO_Z),
                List.of("first", "second", "third"),
                "Equal case-insensitive descriptions lost stable order.");
    }

    private static void sortingDoesNotChangeRepositoryOrder() {
        InMemoryExpenseRepository repository = searchRepository();

        find(repository, null, null, null, null, ExpenseSortOrder.AMOUNT_HIGHEST_FIRST);

        assertIds(
                repository.findAll(),
                List.of("food", "transport", "entertainment", "education"),
                "Sorting modified repository order.");
    }

    private static void findExpensesResultIsUnmodifiable() {
        List<Expense> matches = find(
                searchRepository(), null, null, null, null,
                ExpenseSortOrder.ORIGINAL_ORDER);

        expectThrows(
                UnsupportedOperationException.class,
                () -> matches.remove(0),
                "Search result should be unmodifiable.");
    }

    private static void nullSortOrderIsRejected() {
        InMemoryExpenseRepository repository = searchRepository();
        repository.resetFindAllCalls();

        expectThrows(
                ValidationException.class,
                () -> find(repository, null, null, null, null, null),
                "Null sort order should be rejected.");
        assertEquals(0, repository.getFindAllCalls(),
                "Null sort order should fail before loading repository data.");
    }

    private static void emptySummaryContainsZeros() {
        ExpenseSummary summary =
                new ExpenseService(new InMemoryExpenseRepository()).calculateSummary(List.of());

        assertEquals(0, summary.getExpenseCount(), "Empty count should be zero.");
        assertMoney("0.00", summary.getTotalAmount(), "Empty total should be 0.00.");
        assertMoney("0.00", summary.getAverageAmount(), "Empty average should be 0.00.");
        for (Category category : Category.values()) {
            assertMoney(
                    "0.00",
                    summary.getTotalForCategory(category),
                    "Empty category total should be 0.00.");
        }
    }

    private static void summaryCountsExpenses() {
        InMemoryExpenseRepository repository = searchRepository();
        ExpenseSummary summary =
                new ExpenseService(repository).calculateSummary(repository.findAll());

        assertEquals(4, summary.getExpenseCount(), "Summary count is incorrect.");
    }

    private static void summaryCalculatesExactTotal() {
        InMemoryExpenseRepository repository = searchRepository();
        ExpenseSummary summary =
                new ExpenseService(repository).calculateSummary(repository.findAll());

        assertMoney("77.00", summary.getTotalAmount(), "Summary total is incorrect.");
    }

    private static void summaryAverageUsesHalfUp() {
        List<Expense> expenses = List.of(
                expense("one", "One", "10.00", BASE_DATE, Category.FOOD, ""),
                expense("two", "Two", "10.01", BASE_DATE, Category.FOOD, ""),
                expense("three", "Three", "10.01", BASE_DATE, Category.FOOD, ""));

        ExpenseSummary summary =
                new ExpenseService(new InMemoryExpenseRepository()).calculateSummary(expenses);

        assertMoney("10.01", summary.getAverageAmount(),
                "Average should use two-decimal HALF_UP rounding.");
    }

    private static void summaryIncludesEveryCategory() {
        ExpenseSummary summary = new ExpenseService(new InMemoryExpenseRepository())
                .calculateSummary(List.of(simpleExpense("one")));

        assertEquals(Category.values().length, summary.getTotalsByCategory().size(),
                "Summary does not contain every category.");
        for (Category category : Category.values()) {
            assertTrue(summary.getTotalsByCategory().containsKey(category),
                    "Summary is missing category " + category.name() + ".");
        }
    }

    private static void categoryTotalsAreCorrect() {
        List<Expense> expenses = List.of(
                expense("food-one", "One", "12.00", BASE_DATE, Category.FOOD, ""),
                expense("food-two", "Two", "8.50", BASE_DATE, Category.FOOD, ""),
                expense("bill", "Three", "5.00", BASE_DATE, Category.BILLS, ""));

        ExpenseSummary summary =
                new ExpenseService(new InMemoryExpenseRepository()).calculateSummary(expenses);

        assertMoney("20.50", summary.getTotalForCategory(Category.FOOD),
                "Food total is incorrect.");
        assertMoney("5.00", summary.getTotalForCategory(Category.BILLS),
                "Bills total is incorrect.");
    }

    private static void unusedCategoryReturnsZero() {
        ExpenseSummary summary = new ExpenseService(new InMemoryExpenseRepository())
                .calculateSummary(List.of(simpleExpense("one")));

        assertMoney("0.00", summary.getTotalForCategory(Category.ENTERTAINMENT),
                "Unused category should return 0.00.");
    }

    private static void categoryTotalsMapIsUnmodifiable() {
        ExpenseSummary summary = new ExpenseService(new InMemoryExpenseRepository())
                .calculateSummary(List.of(simpleExpense("one")));

        expectThrows(
                UnsupportedOperationException.class,
                () -> summary.getTotalsByCategory().put(Category.FOOD, BigDecimal.ZERO),
                "Category totals map should be unmodifiable.");
    }

    private static void overallSummaryUsesRepositoryExpenses() {
        InMemoryExpenseRepository repository = searchRepository();

        ExpenseSummary summary = new ExpenseService(repository).calculateOverallSummary();

        assertEquals(4, summary.getExpenseCount(), "Overall summary count is incorrect.");
        assertMoney("77.00", summary.getTotalAmount(), "Overall summary total is incorrect.");
    }

    private static void filteredResultCanBeSummarized() {
        ExpenseService service = new ExpenseService(searchRepository());
        List<Expense> filtered = service.findExpenses(
                null,
                null,
                BASE_DATE.plusDays(2),
                BASE_DATE.plusDays(2),
                ExpenseSortOrder.ORIGINAL_ORDER);

        ExpenseSummary summary = service.calculateSummary(filtered);

        assertEquals(2, summary.getExpenseCount(), "Filtered summary count is incorrect.");
        assertMoney("45.00", summary.getTotalAmount(), "Filtered summary total is incorrect.");
    }

    private static void nullSummaryInputIsRejected() {
        expectThrows(
                ValidationException.class,
                () -> new ExpenseService(new InMemoryExpenseRepository()).calculateSummary(null),
                "Null summary input should be rejected.");
    }

    private static void nullSummaryElementIsRejected() {
        List<Expense> expenses = Arrays.asList(simpleExpense("one"), null);

        expectThrows(
                ValidationException.class,
                () -> new ExpenseService(new InMemoryExpenseRepository())
                        .calculateSummary(expenses),
                "Null summary elements should be rejected.");
    }

    private static void nullSummaryCategoryIsRejected() {
        ExpenseSummary summary = new ExpenseService(new InMemoryExpenseRepository())
                .calculateSummary(List.of());

        expectThrows(
                ValidationException.class,
                () -> summary.getTotalForCategory(null),
                "Null summary category should be rejected.");
    }

    private static void summaryDefensivelyCopiesCategoryTotals() {
        Map<Category, BigDecimal> sourceTotals = new LinkedHashMap<>();
        sourceTotals.put(Category.FOOD, new BigDecimal("3.00"));
        ExpenseSummary summary = new ExpenseSummary(
                1,
                new BigDecimal("3.00"),
                new BigDecimal("3.00"),
                sourceTotals);

        sourceTotals.put(Category.FOOD, new BigDecimal("99.00"));

        assertMoney("3.00", summary.getTotalForCategory(Category.FOOD),
                "Summary retained the caller's mutable map.");
        assertMoney("0.00", summary.getTotalForCategory(Category.OTHER),
                "Missing categories should be initialized to 0.00.");
    }

    private static void validationExceptionIsNotHidden() {
        Throwable exception = expectThrows(
                ValidationException.class,
                () -> new ExpenseService(new InMemoryExpenseRepository()).createExpense(
                        "",
                        new BigDecimal("12.50"),
                        BASE_DATE,
                        Category.FOOD,
                        ""),
                "Service should expose model validation failures.");

        assertEquals(ValidationException.class, exception.getClass(),
                "Validation failure was wrapped in another exception.");
    }

    private static void repositoryExceptionIsNotHidden() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        RepositoryException expected = new RepositoryException("Test persistence failure.");
        repository.failNextAdd(expected);
        ExpenseService service = new ExpenseService(repository);

        RepositoryException actual = expectThrows(
                RepositoryException.class,
                () -> service.createExpense(
                        "Lunch",
                        new BigDecimal("12.50"),
                        BASE_DATE,
                        Category.FOOD,
                        ""),
                "Service should expose repository failures.");

        assertSame(expected, actual, "Repository failure was wrapped or replaced.");
    }

    private static void validationFailuresDoNotChangeData() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        Expense original = simpleExpense("existing");
        repository.add(original);
        ExpenseService service = new ExpenseService(repository);

        expectThrows(
                ValidationException.class,
                () -> service.createExpense(
                        "", new BigDecimal("10.00"), BASE_DATE, Category.FOOD, ""),
                "Invalid creation should fail.");
        expectThrows(
                ValidationException.class,
                () -> service.updateExpense(
                        "existing", "Changed", BigDecimal.ZERO, BASE_DATE, Category.FOOD, ""),
                "Invalid update should fail.");
        expectThrows(
                ValidationException.class,
                () -> service.findExpenses(
                        null,
                        null,
                        BASE_DATE.plusDays(1),
                        BASE_DATE,
                        ExpenseSortOrder.ORIGINAL_ORDER),
                "Invalid search range should fail.");

        assertEquals(1, repository.size(), "Validation failures changed repository size.");
        assertSame(original, repository.findById("existing").orElseThrow(),
                "Validation failures replaced stored data.");
        assertEquals("Test expense", original.getDescription(),
                "Validation failures mutated stored data.");
    }

    private static void findExpensesLoadsSnapshotOnce() {
        InMemoryExpenseRepository repository = searchRepository();
        repository.resetFindAllCalls();

        find(repository, "a", null, null, null, ExpenseSortOrder.DESCRIPTION_A_TO_Z);

        assertEquals(1, repository.getFindAllCalls(),
                "findExpenses should load one repository snapshot.");
    }

    private static void unicodeSearchIsPreserved() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(expense(
                "unicode",
                "বাংলা বই",
                "25.00",
                BASE_DATE,
                Category.EDUCATION,
                "ক্যাফে"));

        assertIds(
                find(repository, "বাংলা", null, null, null,
                        ExpenseSortOrder.ORIGINAL_ORDER),
                List.of("unicode"),
                "Unicode search text was not preserved.");
    }

    private static List<Expense> find(
            InMemoryExpenseRepository repository,
            String searchText,
            Category category,
            LocalDate startDate,
            LocalDate endDate,
            ExpenseSortOrder sortOrder) {
        return new ExpenseService(repository).findExpenses(
                searchText, category, startDate, endDate, sortOrder);
    }

    private static InMemoryExpenseRepository searchRepository() {
        InMemoryExpenseRepository repository = new InMemoryExpenseRepository();
        repository.add(expense(
                "food",
                "Coffee Shop",
                "12.00",
                BASE_DATE,
                Category.FOOD,
                "Morning meeting"));
        repository.add(expense(
                "transport",
                "Bus Pass",
                "30.00",
                BASE_DATE.plusDays(2),
                Category.TRANSPORT,
                "Monthly commute"));
        repository.add(expense(
                "entertainment",
                "Cinema Ticket",
                "15.00",
                BASE_DATE.plusDays(2),
                Category.ENTERTAINMENT,
                "Friends night"));
        repository.add(expense(
                "education",
                "book",
                "20.00",
                BASE_DATE.plusDays(5),
                Category.EDUCATION,
                "Java course"));
        return repository;
    }

    private static Expense simpleExpense(String id) {
        return expense(
                id, "Test expense", "10.00", BASE_DATE, Category.OTHER, "Test note");
    }

    private static Expense expense(
            String id,
            String description,
            String amount,
            LocalDate date,
            Category category,
            String notes) {
        return new Expense(
                id, description, new BigDecimal(amount), date, category, notes);
    }

    private static void assertIds(
            List<Expense> expenses, List<String> expectedIds, String message) {
        List<String> actualIds = expenses.stream().map(Expense::getId).toList();
        assertEquals(expectedIds, actualIds, message);
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(new BigDecimal(expected), actual, message);
        assertEquals(2, actual.scale(), message + " Scale should be two.");
    }

    private static void runTest(String name, TestCase test) {
        try {
            test.run();
            passedTests++;
        } catch (Throwable exception) {
            throw new AssertionError("Service test failed: " + name, exception);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, TestCase action, String message) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return expectedType.cast(exception);
            }
            throw new AssertionError(
                    message + " Expected " + expectedType.getSimpleName()
                    + " but caught " + exception.getClass().getSimpleName() + ".",
                    exception);
        }
        throw new AssertionError(
                message + " Expected " + expectedType.getSimpleName() + ".");
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

    private static void assertNotSame(Object unexpected, Object actual, String message) {
        if (unexpected == actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + " Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    @FunctionalInterface
    private interface TestCase {

        void run() throws Exception;
    }

    private static final class InMemoryExpenseRepository implements ExpenseRepository {

        private final List<Expense> expenses = new ArrayList<>();
        private int findAllCalls;
        private RepositoryException nextAddFailure;

        @Override
        public List<Expense> findAll() {
            findAllCalls++;
            return List.copyOf(expenses);
        }

        @Override
        public Optional<Expense> findById(String id) {
            return expenses.stream()
                    .filter(expense -> expense.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Expense expense) {
            if (nextAddFailure != null) {
                RepositoryException failure = nextAddFailure;
                nextAddFailure = null;
                throw failure;
            }
            requireExpense(expense);
            if (findById(expense.getId()).isPresent()) {
                throw new RepositoryException("Expense ID already exists: " + expense.getId());
            }
            expenses.add(expense);
        }

        @Override
        public void update(Expense expense) {
            requireExpense(expense);
            int index = indexOf(expense.getId());
            if (index < 0) {
                throw new RepositoryException("Expense ID does not exist: " + expense.getId());
            }
            expenses.set(index, expense);
        }

        @Override
        public boolean deleteById(String id) {
            int index = indexOf(id);
            if (index < 0) {
                return false;
            }
            expenses.remove(index);
            return true;
        }

        int size() {
            return expenses.size();
        }

        int getFindAllCalls() {
            return findAllCalls;
        }

        void resetFindAllCalls() {
            findAllCalls = 0;
        }

        void failNextAdd(RepositoryException failure) {
            nextAddFailure = failure;
        }

        private int indexOf(String id) {
            for (int index = 0; index < expenses.size(); index++) {
                if (expenses.get(index).getId().equals(id)) {
                    return index;
                }
            }
            return -1;
        }

        private static void requireExpense(Expense expense) {
            if (expense == null) {
                throw new RepositoryException("Expense is required.");
            }
        }
    }
}
