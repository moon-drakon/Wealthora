package com.wealthora.server.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.UUID;

/** Removes one unlinked live OAuth flow created by controlled verification. */
public final class LiveGoogleOAuthFlowCleanup {

    private LiveGoogleOAuthFlowCleanup() {
    }

    public static void main(String[] arguments) throws Exception {
        UUID flowId = UUID.fromString(required("WEALTHORA_LIVE_OAUTH_FLOW_ID"));
        int removed;
        try (Connection connection = databaseConnection()) {
            connection.setAutoCommit(false);
            try {
                requireUnlinkedFixture(connection, flowId);
                try (PreparedStatement delete = connection.prepareStatement(
                        "delete from google_oauth_flows where id = ? "
                        + "and user_id is null and flow_status in "
                        + "('PENDING', 'FAILED')")) {
                    delete.setObject(1, flowId);
                    removed = delete.executeUpdate();
                }
                require(removed == 1,
                        "The scoped OAuth flow was not removed.");
                connection.commit();
            } catch (Throwable failure) {
                connection.rollback();
                throw failure;
            }
        }
        System.out.println("DisposableGoogleOAuthFlowsRemoved: " + removed);
        System.out.println("LiveGoogleOAuthFlowCleanup: PASS");
    }

    private static void requireUnlinkedFixture(
            Connection connection, UUID flowId) throws Exception {
        try (PreparedStatement find = connection.prepareStatement(
                "select flow_status, user_id from google_oauth_flows "
                + "where id = ?")) {
            find.setObject(1, flowId);
            try (ResultSet result = find.executeQuery()) {
                require(result.next(), "The scoped OAuth flow is absent.");
                String status = result.getString(1);
                require(result.getObject(2) == null,
                        "Cleanup refused a linked OAuth flow.");
                require("PENDING".equals(status) || "FAILED".equals(status),
                        "Cleanup refused a completed OAuth flow.");
                require(!result.next(),
                        "The scoped OAuth flow identifier is not unique.");
            }
        }
    }

    private static Connection databaseConnection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", required("DATABASE_USERNAME"));
        properties.setProperty("password", required("DATABASE_PASSWORD"));
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");
        return DriverManager.getConnection(
                required("DATABASE_URL"), properties);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(), name + " is required.");
        return value.strip();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
