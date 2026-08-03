package com.wealthora.server.repository;

import com.wealthora.server.domain.UserRoleAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<
        UserRoleAssignment, UserRoleAssignment.Key> {

    List<UserRoleAssignment> findByUserId(UUID userId);
}
