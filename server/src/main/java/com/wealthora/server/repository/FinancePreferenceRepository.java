package com.wealthora.server.repository;

import com.wealthora.server.domain.FinancePreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancePreferenceRepository
        extends JpaRepository<FinancePreference, UUID> {
}
