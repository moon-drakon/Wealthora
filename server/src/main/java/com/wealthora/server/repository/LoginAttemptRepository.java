package com.wealthora.server.repository;

import com.wealthora.server.domain.LoginAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository
        extends JpaRepository<LoginAttempt, UUID> {
}
