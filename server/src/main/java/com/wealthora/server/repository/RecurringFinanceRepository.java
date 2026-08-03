package com.wealthora.server.repository;

import com.wealthora.server.domain.RecurringFinanceRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringFinanceRepository
        extends JpaRepository<RecurringFinanceRecord, UUID> {
    Page<RecurringFinanceRecord> findByUserId(UUID userId, Pageable pageable);
    Optional<RecurringFinanceRecord> findByUserIdAndExternalId(
            UUID userId, String externalId);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
