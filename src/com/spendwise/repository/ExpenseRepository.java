package com.spendwise.repository;

import com.spendwise.model.Expense;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {

    /**
     * Returns an immutable snapshot in repository order.
     */
    List<Expense> findAll();

    Optional<Expense> findById(String id);

    /**
     * Adds an expense unless its ID already exists.
     */
    void add(Expense expense);

    /**
     * Replaces the expense with the same ID.
     */
    void update(Expense expense);

    /**
     * Removes an expense and reports whether a record was removed.
     */
    boolean deleteById(String id);
}
