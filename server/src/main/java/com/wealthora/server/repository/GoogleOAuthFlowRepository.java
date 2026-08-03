package com.wealthora.server.repository;

import com.wealthora.server.domain.GoogleOAuthFlow;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleOAuthFlowRepository
        extends JpaRepository<GoogleOAuthFlow, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select flow from GoogleOAuthFlow flow where flow.stateHash = :stateHash")
    Optional<GoogleOAuthFlow> findByStateHash(
            @Param("stateHash") String stateHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select flow from GoogleOAuthFlow flow where flow.id = :id and flow.pollSecretHash = :secret")
    Optional<GoogleOAuthFlow> findForPoll(
            @Param("id") UUID id, @Param("secret") String secretHash);
}
