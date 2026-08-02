package com.spendwise.auth.admin;

public record AdminOverview(
        int totalUsers,
        int activeUsers,
        int suspendedUsers,
        int owners,
        int administrators,
        int standardUsers,
        int failedLoginAttempts,
        String lastBackup,
        String storageStatus) {
}
