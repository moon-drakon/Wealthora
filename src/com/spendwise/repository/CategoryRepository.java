package com.spendwise.repository;

import com.spendwise.model.Category;
import java.util.List;

public interface CategoryRepository {

    /**
     * Returns an immutable snapshot containing persisted custom categories only.
     */
    List<Category> findAll();

    void add(Category category);

    void update(Category category);
}
