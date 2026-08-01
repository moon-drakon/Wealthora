package com.spendwise.repository;

import com.spendwise.model.Income;
import java.util.List;
import java.util.Optional;

public interface IncomeRepository {

    List<Income> findAll();

    Optional<Income> findById(String id);

    void add(Income income);

    void update(Income income);

    boolean deleteById(String id);
}
