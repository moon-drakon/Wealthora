package com.spendwise.repository;

import com.spendwise.model.RecurringEntry;
import java.util.List;
import java.util.Optional;

public interface RecurringEntryRepository {

    List<RecurringEntry> findAll();

    Optional<RecurringEntry> findById(String identifier);

    void add(RecurringEntry entry);

    void update(RecurringEntry entry);
}
