package com.wealthora.server.repository;

import com.wealthora.server.domain.EmailVerification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository
        extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findFirstByUserIdOrderBySentAtDesc(UUID userId);
}
