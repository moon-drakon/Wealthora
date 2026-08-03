package com.wealthora.server.api;

public record AdminOverviewResponse(
        int totalUsers,
        int activeUsers,
        int pendingApproval,
        int pendingVerification,
        int suspendedUsers,
        int disabledUsers,
        int owners,
        int administrators,
        int standardUsers,
        int failedLoginAttempts,
        long auditEvents) {
}
