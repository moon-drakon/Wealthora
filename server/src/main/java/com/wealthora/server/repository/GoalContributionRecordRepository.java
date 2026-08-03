package com.wealthora.server.repository;

import com.wealthora.server.domain.GoalContributionRecord;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalContributionRecordRepository
        extends JpaRepository<GoalContributionRecord, UUID> {
    Page<GoalContributionRecord> findByUserIdAndGoalId(
            UUID userId, UUID goalId, Pageable pageable);
    boolean existsByUserIdAndExternalId(UUID userId, String externalId);

    @Query("select coalesce(sum(item.amount), 0) from GoalContributionRecord item "
            + "where item.userId = :userId and item.goalId = :goalId")
    BigDecimal totalForGoal(
            @Param("userId") UUID userId, @Param("goalId") UUID goalId);
}
