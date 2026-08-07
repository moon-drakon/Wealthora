package com.wealthora.server.repository;

import com.wealthora.server.domain.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.email = :email")
    Optional<UserAccount> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    java.util.List<UserAccount> findAllByOrderByCreatedAtAsc();

    java.util.List<UserAccount> findByAccountStatusOrderByCreatedAtAsc(
            com.wealthora.server.domain.AccountStatus status);
}
