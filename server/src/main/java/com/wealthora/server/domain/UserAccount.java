package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "student_id", length = 80)
    private String studentId;
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 40)
    private AccountStatus accountStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected UserAccount() {
    }

    public UserAccount(
            UUID id, String fullName, String email, String studentId,
            AccountStatus accountStatus, Instant now) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.studentId = studentId;
        this.accountStatus = accountStatus;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getStudentId() { return studentId; }
    public boolean isEmailVerified() { return emailVerified; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }

    public void verifyEmail(AccountStatus nextStatus, Instant now) {
        emailVerified = true;
        accountStatus = nextStatus;
        updatedAt = now;
    }
}
