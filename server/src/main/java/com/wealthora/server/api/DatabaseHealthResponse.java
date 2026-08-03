package com.wealthora.server.api;

public record DatabaseHealthResponse(
        String status,
        String databaseProduct,
        long appliedMigrations,
        long users,
        long activeSessions) {
}
