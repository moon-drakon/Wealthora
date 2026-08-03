package com.wealthora.server.repository;

import com.wealthora.server.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    java.util.List<UserAccount> findAllByOrderByCreatedAtAsc();

    java.util.List<UserAccount> findByAccountStatusOrderByCreatedAtAsc(
            com.wealthora.server.domain.AccountStatus status);
}
