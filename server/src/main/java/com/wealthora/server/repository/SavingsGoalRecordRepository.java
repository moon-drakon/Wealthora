package com.wealthora.server.repository;

import com.wealthora.server.domain.SavingsGoalRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRecordRepository
        extends JpaRepository<SavingsGoalRecord, UUID> {
    Page<SavingsGoalRecord> findByUserId(UUID userId, Pageable pageable);
    Optional<SavingsGoalRecord> findByUserIdAndExternalId(
            UUID userId, String externalId);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
