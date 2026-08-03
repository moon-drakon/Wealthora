package com.wealthora.server.repository;

import com.wealthora.server.domain.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findFirstByTokenHashOrderByCreatedAtDesc(
            String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(
            UUID userId);

    List<PasswordResetToken> findByUserIdAndConsumedAtIsNull(UUID userId);
}
