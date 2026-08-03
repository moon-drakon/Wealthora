package com.wealthora.server.repository;

import com.wealthora.server.domain.DebtRecordEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtRecordEntityRepository
        extends JpaRepository<DebtRecordEntity, UUID> {
    Page<DebtRecordEntity> findByUserId(UUID userId, Pageable pageable);
    Optional<DebtRecordEntity> findByUserIdAndExternalId(
            UUID userId, String externalId);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
