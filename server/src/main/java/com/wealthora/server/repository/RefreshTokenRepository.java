package com.wealthora.server.repository;

import com.wealthora.server.domain.RefreshTokenRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshTokenRecord, UUID> {

    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);
}
