package com.wealthora.server.repository;

import com.wealthora.server.domain.DebtRepaymentRecord;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtRepaymentRecordRepository
        extends JpaRepository<DebtRepaymentRecord, UUID> {
    Page<DebtRepaymentRecord> findByUserIdAndDebtId(
            UUID userId, UUID debtId, Pageable pageable);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);

    @Query("select coalesce(sum(item.amount), 0) from DebtRepaymentRecord item "
            + "where item.userId = :userId and item.debtId = :debtId")
    BigDecimal totalForDebt(
            @Param("userId") UUID userId, @Param("debtId") UUID debtId);
}
