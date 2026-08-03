package com.wealthora.server.repository;

import com.wealthora.server.domain.FinanceTransfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTransferRepository
        extends JpaRepository<FinanceTransfer, UUID> {
    Page<FinanceTransfer> findByUserId(UUID userId, Pageable pageable);
    Optional<FinanceTransfer> findByUserIdAndExternalId(
            UUID userId, String externalId);
    Optional<FinanceTransfer> findByUserIdAndId(UUID userId, UUID id);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
