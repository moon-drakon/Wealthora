package com.wealthora.server.repository;

import com.wealthora.server.domain.FinanceCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCategoryRepository
        extends JpaRepository<FinanceCategory, UUID> {
    Page<FinanceCategory> findByUserId(UUID userId, Pageable pageable);
    Optional<FinanceCategory> findByUserIdAndExternalId(
            UUID userId, String externalId);
    Optional<FinanceCategory> findByUserIdAndId(UUID userId, UUID id);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
