package com.wealthora.server.repository;

import com.wealthora.server.domain.AuditLogEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository
        extends JpaRepository<AuditLogEntry, UUID> {
}
