package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.validation.ExpenseValidator;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class CsvExpenseRepository implements ExpenseRepository {

    private final Path csvPath;
    private final Function<String, Category> categoryResolver;
    private final Function<String, Account> accountResolver;

    public CsvExpenseRepository(Path csvPath) {
        this(csvPath, Category::valueOf, CsvExpenseRepository::defaultAccount);
    }

    public CsvExpenseRepository(
            Path csvPath, Function<String, Category> categoryResolver) {
        this(csvPath, categoryResolver, CsvExpenseRepository::defaultAccount);
    }

    public CsvExpenseRepository(
            Path csvPath,
            Function<String, Category> categoryResolver,
            Function<String, Account> accountResolver) {
        this.csvPath = Objects.requireNonNull(csvPath, "CSV path is required.")
                .toAbsolutePath()
                .normalize();
        this.categoryResolver = Objects.requireNonNull(
                categoryResolver, "Category resolver is required.");
        this.accountResolver = Objects.requireNonNull(
                accountResolver, "Account resolver is required.");
    }

    @Override
    public List<Expense> findAll() {
        try {
            if (Files.notExists(csvPath)) {
                return List.of();
            }
            String csvText = Files.readString(csvPath, StandardCharsets.UTF_8);
            if (csvText.isEmpty()) {
                return List.of();
            }
            return CsvExpenseCodec.decode(
                    csvText, categoryResolver, accountResolver);
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException("Could not read expense CSV data.", exception);
        }
    }

    @Override
    public Optional<Expense> findById(String id) {
        String normalizedId = validateRepositoryId(id);
        return findAll().stream()
                .filter(expense -> expense.getId().equals(normalizedId))
                .findFirst();
    }

    @Override
    public void add(Expense expense) {
        Expense requiredExpense = requireExpense(expense);
        List<Expense> currentExpenses = findAll();
        if (containsId(currentExpenses, requiredExpense.getId())) {
            throw new RepositoryException(
                    "Expense ID already exists: " + requiredExpense.getId());
        }

        List<Expense> updatedExpenses = new ArrayList<>(currentExpenses);
        updatedExpenses.add(requiredExpense);
        writeSnapshot(updatedExpenses);
    }

    @Override
    public void update(Expense expense) {
        Expense requiredExpense = requireExpense(expense);
        List<Expense> updatedExpenses = new ArrayList<>(findAll());
        int existingIndex = indexOfId(updatedExpenses, requiredExpense.getId());
        if (existingIndex < 0) {
            throw new RepositoryException(
                    "Expense ID does not exist: " + requiredExpense.getId());
        }

        updatedExpenses.set(existingIndex, requiredExpense);
        writeSnapshot(updatedExpenses);
    }

    @Override
    public boolean deleteById(String id) {
        String normalizedId = validateRepositoryId(id);
        List<Expense> updatedExpenses = new ArrayList<>(findAll());
        int existingIndex = indexOfId(updatedExpenses, normalizedId);
        if (existingIndex < 0) {
            return false;
        }

        updatedExpenses.remove(existingIndex);
        writeSnapshot(updatedExpenses);
        return true;
    }

    private void writeSnapshot(List<Expense> expenses) {
        String csvText = CsvExpenseCodec.encode(List.copyOf(expenses));
        Path parentDirectory = csvPath.getParent();
        if (parentDirectory == null) {
            throw new RepositoryException("CSV path must have a parent directory.");
        }
        Path temporaryFile = null;

        try {
            Files.createDirectories(parentDirectory);
            temporaryFile = Files.createTempFile(
                    parentDirectory, ".spendwise-expenses-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(csvText);
                writer.flush();
            }
            try (FileChannel channel = FileChannel.open(
                    temporaryFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            replaceWithTemporaryFile(temporaryFile);
            temporaryFile = null;
        } catch (IOException | SecurityException exception) {
            cleanUpTemporaryFile(temporaryFile, exception);
            throw new RepositoryException("Could not save expense CSV data.", exception);
        }
    }

    private void replaceWithTemporaryFile(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    csvPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupportedException) {
            try {
                Files.move(
                        temporaryFile,
                        csvPath,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackException) {
                fallbackException.addSuppressed(unsupportedException);
                throw fallbackException;
            }
        }
    }

    private static void cleanUpTemporaryFile(Path temporaryFile, Throwable originalFailure) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException | SecurityException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private static Expense requireExpense(Expense expense) {
        if (expense == null) {
            throw new RepositoryException("Expense is required.");
        }
        return expense;
    }

    private static String validateRepositoryId(String id) {
        try {
            return ExpenseValidator.validateId(id);
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("Invalid expense ID: " + exception.getMessage(), exception);
        }
    }

    private static boolean containsId(List<Expense> expenses, String id) {
        return indexOfId(expenses, id) >= 0;
    }

    private static int indexOfId(List<Expense> expenses, String id) {
        for (int index = 0; index < expenses.size(); index++) {
            if (expenses.get(index).getId().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static Account defaultAccount(String identifier) {
        if (Account.DEFAULT_IDENTIFIER.equals(identifier)) {
            return Account.DEFAULT;
        }
        throw new IllegalArgumentException(
                "Unknown account identifier: " + identifier);
    }
}
