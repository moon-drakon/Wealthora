package com.wealthora.server.repository;

import com.wealthora.server.domain.SessionRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRecordRepository
        extends JpaRepository<SessionRecord, UUID> {

    Optional<SessionRecord> findByAccessTokenHash(String accessTokenHash);

    List<SessionRecord> findByUserIdAndRevokedAtIsNull(UUID userId);
}
