package com.wealthora.server.repository;

import com.wealthora.server.domain.FinanceAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceAccountRepository
        extends JpaRepository<FinanceAccount, UUID> {

    Page<FinanceAccount> findByUserId(UUID userId, Pageable pageable);
    Optional<FinanceAccount> findByUserIdAndExternalId(
            UUID userId, String externalId);
    Optional<FinanceAccount> findByUserIdAndId(UUID userId, UUID id);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from FinanceAccount account "
            + "where account.userId = :userId and account.id = :id")
    Optional<FinanceAccount> findOwnedForUpdate(
            @Param("userId") UUID userId, @Param("id") UUID id);
}
