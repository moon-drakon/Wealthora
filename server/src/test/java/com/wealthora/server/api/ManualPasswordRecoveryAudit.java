package com.wealthora.server.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * Performs a read-only, anonymized audit of the manual password-recovery gate.
 */
public final class ManualPasswordRecoveryAudit {

    private ManualPasswordRecoveryAudit() {
    }

    public static void main(String[] arguments) {
        try {
            new ManualPasswordRecoveryAudit().verify();
        } catch (Throwable failure) {
            System.err.println(
                    "Manual password-recovery audit failed. "
                            + "Category=VERIFICATION");
            System.exit(1);
        }
    }

    private void verify() throws Exception {
        Instant registrationGateStartedAt = Instant.parse(
                requiredEnvironment("WEALTHORA_MANUAL_GATE_STARTED_AT"));
        Properties properties = databaseProperties();

        RecoveryState state;
        try (Connection connection = DriverManager.getConnection(
                requiredEnvironment("DATABASE_URL"), properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            UUID registrationUserId = findOneActivatedUser(
                    connection, registrationGateStartedAt);
            UUID recoveryUserId = findOneRecoveryUser(
                    connection, registrationGateStartedAt);
            state = recoveryState(
                    connection, recoveryUserId,
                    recoveryUserId.equals(registrationUserId));
            connection.rollback();
        }

        System.out.println("AccountVerified: "
                + pass(state.emailVerified()));
        System.out.println("AccountStatus: " + state.accountStatus());
        System.out.println("LocalAccountShadow: "
                + (state.localAccountShadow() ? "PRESENT" : "ABSENT"));
        System.out.println("RecoveryAccountMatch: "
                + (state.registrationAccountMatch()
                        ? "PASS" : "DIFFERENT_ACCOUNT"));
        System.out.println("PasswordResetRequested: "
                + pass(state.resetRequestedAt() != null));
        System.out.println("PasswordResetCompleted: "
                + pass(state.resetCompletedAt() != null));
        System.out.println("ResetTokenConsumed: "
                + pass(state.resetTokenConsumed()));
        System.out.println("PasswordIdentityUpdated: "
                + pass(state.passwordIdentityUpdated()));
        System.out.println("PreResetSessionsRevoked: "
                + pass(state.preResetSessionsRevoked()));
        System.out.println("PostResetLoginAttempt: "
                + state.postResetLoginOutcome());
        System.out.println("AccountLock: "
                + (state.accountLocked() ? "ACTIVE" : "CLEAR"));
        System.out.println("ActiveCloudSession: "
                + pass(state.activeCloudSession()));

        require(state.emailVerified());
        require("ACTIVE".equals(state.accountStatus()));
        require(state.resetRequestedAt() != null);
        require(state.resetCompletedAt() != null);
        require(state.resetTokenConsumed());
        require(state.passwordIdentityUpdated());
        require(state.preResetSessionsRevoked());
    }

    private RecoveryState recoveryState(
            Connection connection, UUID userId,
            boolean registrationAccountMatch)
            throws Exception {
        AccountState account = accountState(connection, userId);
        boolean localAccountShadow = localAccountShadow(
                account.email());
        Instant requestedAt = latestAuditTime(
                connection, userId, "PASSWORD_RESET_REQUESTED");
        Instant completedAt = latestAuditTime(
                connection, userId, "PASSWORD_RESET_COMPLETED");
        boolean consumed = latestResetTokenConsumed(connection, userId);
        boolean identityUpdated = completedAt != null
                && count(connection,
                        "select count(*) from authentication_identities "
                                + "where user_id = ? and provider = 'PASSWORD' "
                                + "and updated_at >= ?",
                        userId, completedAt) == 1;
        boolean oldSessionsRevoked = completedAt != null
                && count(connection,
                        "select count(*) from sessions where user_id = ? "
                                + "and created_at < ? and revoked_at is null",
                        userId, completedAt) == 0;
        String loginOutcome = completedAt == null
                ? "MISSING" : latestLoginOutcome(
                        connection, userId, completedAt);
        boolean activeSession = completedAt != null
                && count(connection,
                        "select count(*) from sessions where user_id = ? "
                                + "and created_at >= ? and revoked_at is null "
                                + "and expires_at > current_timestamp",
                        userId, completedAt) >= 1;
        return new RecoveryState(
                account.emailVerified(),
                account.accountStatus(),
                account.accountLocked(),
                localAccountShadow,
                registrationAccountMatch,
                requestedAt,
                completedAt,
                consumed,
                identityUpdated,
                oldSessionsRevoked,
                loginOutcome,
                activeSession);
    }

    private AccountState accountState(
            Connection connection, UUID userId) throws Exception {
        String sql = "select email, email_verified, account_status, "
                + "(locked_until is not null "
                + "and locked_until > current_timestamp) "
                + "from users where id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                AccountState state = new AccountState(
                        result.getString(1),
                        result.getBoolean(2),
                        result.getString(3),
                        result.getBoolean(4));
                require(!result.next());
                return state;
            }
        }
    }

    private Instant latestAuditTime(
            Connection connection, UUID userId, String action)
            throws Exception {
        String sql = "select max(occurred_at) from audit_logs "
                + "where actor_user_id = ? and action = ? "
                + "and outcome = 'SUCCESS'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setString(2, action);
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                Timestamp value = result.getTimestamp(1);
                return value == null ? null : value.toInstant();
            }
        }
    }

    private boolean latestResetTokenConsumed(
            Connection connection, UUID userId) throws Exception {
        String sql = "select consumed_at is not null "
                + "from password_reset_tokens where user_id = ? "
                + "order by created_at desc limit 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private String latestLoginOutcome(
            Connection connection, UUID userId, Instant completedAt)
            throws Exception {
        String sql = "select successful from login_attempts "
                + "where user_id = ? and attempted_at >= ? "
                + "order by attempted_at desc limit 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setTimestamp(2, Timestamp.from(completedAt));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return "MISSING";
                }
                return result.getBoolean(1) ? "SUCCESS" : "DENIED";
            }
        }
    }

    private UUID findOneActivatedUser(
            Connection connection, Instant gateStartedAt) throws Exception {
        String sql = "select u.id from users u "
                + "where u.created_at >= ? "
                + "and u.email_verified = true "
                + "and u.account_status = 'ACTIVE' "
                + "and lower(u.email) like '%@northsouth.edu' "
                + "and exists (select 1 from authentication_identities i "
                + "where i.user_id = u.id and i.provider = 'PASSWORD')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(gateStartedAt));
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                UUID userId = result.getObject(1, UUID.class);
                require(!result.next());
                return userId;
            }
        }
    }

    private UUID findOneRecoveryUser(
            Connection connection, Instant gateStartedAt) throws Exception {
        String sql = "select distinct u.id from users u "
                + "join audit_logs a on a.actor_user_id = u.id "
                + "where u.email_verified = true "
                + "and u.account_status = 'ACTIVE' "
                + "and lower(u.email) like '%@northsouth.edu' "
                + "and a.occurred_at >= ? "
                + "and a.action = 'PASSWORD_RESET_REQUESTED' "
                + "and a.outcome = 'SUCCESS'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(gateStartedAt));
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                UUID userId = result.getObject(1, UUID.class);
                require(!result.next());
                return userId;
            }
        }
    }

    private int count(
            Connection connection, String sql, UUID userId, Instant time)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setTimestamp(2, Timestamp.from(time));
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                return result.getInt(1);
            }
        }
    }

    private static Properties databaseProperties() {
        Properties properties = new Properties();
        properties.setProperty(
                "user", requiredEnvironment("DATABASE_USERNAME"));
        properties.setProperty(
                "password", requiredEnvironment("DATABASE_PASSWORD"));
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");
        return properties;
    }

    private static String pass(boolean condition) {
        return condition ? "PASS" : "MISSING";
    }

    private static boolean localAccountShadow(String cloudEmail) {
        String localEmails = System.getenv("WEALTHORA_LOCAL_ACCOUNT_EMAILS");
        if (localEmails == null || localEmails.isBlank()) {
            return false;
        }
        return localEmails.lines()
                .map(String::strip)
                .anyMatch(cloudEmail::equalsIgnoreCase);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration.");
        }
        return value;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Verification failed.");
        }
    }

    private record AccountState(
            String email,
            boolean emailVerified,
            String accountStatus,
            boolean accountLocked) {
    }

    private record RecoveryState(
            boolean emailVerified,
            String accountStatus,
            boolean accountLocked,
            boolean localAccountShadow,
            boolean registrationAccountMatch,
            Instant resetRequestedAt,
            Instant resetCompletedAt,
            boolean resetTokenConsumed,
            boolean passwordIdentityUpdated,
            boolean preResetSessionsRevoked,
            String postResetLoginOutcome,
            boolean activeCloudSession) {
    }
}
