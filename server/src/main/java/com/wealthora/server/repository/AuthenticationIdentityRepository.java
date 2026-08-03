package com.wealthora.server.repository;

import com.wealthora.server.domain.AuthenticationIdentity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationIdentityRepository
        extends JpaRepository<AuthenticationIdentity, UUID> {
}
