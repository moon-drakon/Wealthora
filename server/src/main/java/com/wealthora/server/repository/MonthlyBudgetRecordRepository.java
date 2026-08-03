package com.wealthora.server.repository;

import com.wealthora.server.domain.MonthlyBudgetRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetRecordRepository
        extends JpaRepository<MonthlyBudgetRecord, UUID> {
    Page<MonthlyBudgetRecord> findByUserId(UUID userId, Pageable pageable);
    Optional<MonthlyBudgetRecord> findByUserIdAndBudgetMonth(
            UUID userId, String budgetMonth);
}
