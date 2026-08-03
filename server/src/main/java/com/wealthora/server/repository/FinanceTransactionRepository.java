package com.wealthora.server.repository;

import com.wealthora.server.domain.FinanceTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTransactionRepository
        extends JpaRepository<FinanceTransaction, UUID> {
    Page<FinanceTransaction> findByUserId(UUID userId, Pageable pageable);
    Page<FinanceTransaction> findByUserIdAndTransactionType(
            UUID userId, String transactionType, Pageable pageable);
    Optional<FinanceTransaction> findByUserIdAndExternalId(
            UUID userId, String externalId);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);
    List<FinanceTransaction> findByUserIdAndTransferId(
            UUID userId, UUID transferId);
    List<FinanceTransaction> findByUserIdAndOccurredOnBetween(
            UUID userId, LocalDate start, LocalDate end);
}
