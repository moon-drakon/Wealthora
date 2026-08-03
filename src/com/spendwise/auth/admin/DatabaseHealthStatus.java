package com.spendwise.auth.admin;

public record DatabaseHealthStatus(
        String status,
        String databaseProduct,
        long appliedMigrations,
        long users,
        long activeSessions) {
}
