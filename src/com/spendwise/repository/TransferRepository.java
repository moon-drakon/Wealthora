package com.spendwise.repository;

import com.spendwise.model.Transfer;
import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    List<Transfer> findAll();

    Optional<Transfer> findById(String id);

    void add(Transfer transfer);

    void update(Transfer transfer);

    boolean deleteById(String id);
}
