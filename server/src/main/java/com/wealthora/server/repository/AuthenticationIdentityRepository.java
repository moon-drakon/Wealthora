package com.wealthora.server.repository;

import com.wealthora.server.domain.AuthenticationIdentity;
import java.util.UUID;
import java.util.Optional;
import com.wealthora.server.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationIdentityRepository
        extends JpaRepository<AuthenticationIdentity, UUID> {

    Optional<AuthenticationIdentity> findByUserIdAndProvider(
            UUID userId, AuthProvider provider);
}
