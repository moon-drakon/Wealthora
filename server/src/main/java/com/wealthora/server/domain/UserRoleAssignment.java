package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleAssignment.Key.class)
public class UserRoleAssignment {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Id
    @Column(name = "role_name", length = 20)
    private String roleName;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(UUID userId, UserRole role) {
        this.userId = userId;
        this.roleName = role.name();
    }

    public UUID getUserId() { return userId; }
    public String getRoleName() { return roleName; }

    public static final class Key implements Serializable {
        private UUID userId;
        private String roleName;

        public Key() {
        }

        @Override public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(userId, key.userId)
                    && Objects.equals(roleName, key.roleName);
        }

        @Override public int hashCode() {
            return Objects.hash(userId, roleName);
        }
    }
}
