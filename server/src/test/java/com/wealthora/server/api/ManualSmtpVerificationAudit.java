package com.wealthora.server.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class ManualSmtpVerificationAudit {

    private ManualSmtpVerificationAudit() {
    }

    public static void main(String[] arguments) {
        try {
            new ManualSmtpVerificationAudit().verify();
        } catch (Throwable failure) {
            System.err.println(
                    "Manual SMTP audit failed. Category=VERIFICATION");
            System.exit(1);
        }
    }

    private void verify() throws Exception {
        Instant gateStartedAt = Instant.parse(
                requiredEnvironment("WEALTHORA_MANUAL_GATE_STARTED_AT"));
        Properties properties = new Properties();
        properties.setProperty("user",
                requiredEnvironment("DATABASE_USERNAME"));
        properties.setProperty("password",
                requiredEnvironment("DATABASE_PASSWORD"));
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");

        try (Connection connection = DriverManager.getConnection(
                requiredEnvironment("DATABASE_URL"), properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            UUID userId = findOneActivatedUser(
                    connection, gateStartedAt);
            require(count(connection,
                    "select count(*) from email_verifications "
                            + "where user_id = ? and consumed_at is not null",
                    userId) >= 1);
            require(count(connection,
                    "select count(*) from sessions "
                            + "where user_id = ? and revoked_at is null "
                            + "and expires_at > current_timestamp",
                    userId) >= 1);
            require(roles(connection, userId).equals(Set.of("USER")));
            require(auditActions(connection, userId).containsAll(Set.of(
                    "REGISTRATION_CREATED",
                    "EMAIL_VERIFIED",
                    "LOGIN_SUCCESS")));
            connection.rollback();
        }

        System.out.println("SmtpRegistration: PASS");
        System.out.println("VerificationConsumed: PASS");
        System.out.println("AccountActivation: PASS");
        System.out.println("PasswordIdentity: PASS");
        System.out.println("ActiveCloudSession: PASS");
        System.out.println("DefaultUserRole: PASS");
        System.out.println("AuthenticationAudit: PASS");
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

    private int count(
            Connection connection, String sql, UUID userId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                return result.getInt(1);
            }
        }
    }

    private Set<String> roles(
            Connection connection, UUID userId) throws Exception {
        return strings(connection,
                "select role_name from user_roles where user_id = ?",
                userId);
    }

    private Set<String> auditActions(
            Connection connection, UUID userId) throws Exception {
        return strings(connection,
                "select distinct action from audit_logs "
                        + "where actor_user_id = ?",
                userId);
    }

    private Set<String> strings(
            Connection connection, String sql, UUID userId)
            throws Exception {
        Set<String> values = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(result.getString(1));
                }
            }
        }
        return values;
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
}
