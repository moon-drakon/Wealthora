package com.wealthora.server.repository;

import com.wealthora.server.domain.BudgetPlanRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetPlanRecordRepository
        extends JpaRepository<BudgetPlanRecord, UUID> {
    Page<BudgetPlanRecord> findByUserId(UUID userId, Pageable pageable);
    Optional<BudgetPlanRecord> findByUserIdAndExternalId(
            UUID userId, String externalId);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
