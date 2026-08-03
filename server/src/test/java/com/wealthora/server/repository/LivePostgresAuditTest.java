package com.wealthora.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.Socket;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

class LivePostgresAuditTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "accounts",
            "application_settings",
            "audit_logs",
            "authentication_identities",
            "budget_plans",
            "categories",
            "debt_repayments",
            "debts",
            "email_verifications",
            "finance_preferences",
            "flyway_schema_history",
            "goal_contributions",
            "google_oauth_flows",
            "login_attempts",
            "monthly_budgets",
            "password_reset_tokens",
            "recurring_entries",
            "refresh_tokens",
            "roles",
            "savings_goals",
            "schema_migrations",
            "sessions",
            "transactions",
            "transfers",
            "user_roles",
            "users");

    private static final Set<String> EXPECTED_OWNERSHIP_CONSTRAINTS = Set.of(
            "fk_categories_owned_parent",
            "fk_contributions_owned_goal",
            "fk_goals_owned_account",
            "fk_preferences_owned_account",
            "fk_recurring_owned_category",
            "fk_recurring_owned_destination",
            "fk_recurring_owned_source",
            "fk_repayments_owned_debt",
            "fk_transactions_owned_account",
            "fk_transactions_owned_category",
            "fk_transfers_owned_destination",
            "fk_transfers_owned_source");

    @Test
    void configuredPostgresHasTheExpectedProtectedSchema() {
        assumeTrue(Boolean.parseBoolean(
                System.getenv("WEALTHORA_LIVE_DATABASE_AUDIT")));

        String url = requiredEnvironment("DATABASE_URL");
        String username = requiredEnvironment("DATABASE_USERNAME");
        String password = requiredEnvironment("DATABASE_PASSWORD");
        assertSafeJdbcUrl(url);
        assertTlsEndpoint(url);

        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");

        try (Connection connection = DriverManager.getConnection(
                url, properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            assertEquals("PostgreSQL",
                    connection.getMetaData().getDatabaseProductName(),
                    "Live database audit failed: DATABASE_PRODUCT");
            assertEquals(List.of("1", "2", "3", "4", "5"),
                    queryStrings(connection,
                            "select version from flyway_schema_history "
                                    + "where type = 'SQL' and success = true "
                                    + "order by installed_rank"),
                    "Live database audit failed: MIGRATION_HISTORY");
            assertEquals(5, queryInt(connection,
                            "select count(*) from flyway_schema_history "
                                    + "where type = 'SQL' and success = true "
                                    + "and checksum is not null"),
                    "Live database audit failed: MIGRATION_CHECKSUMS");

            Set<String> tables = Set.copyOf(queryStrings(connection,
                    "select table_name from information_schema.tables "
                            + "where table_schema = 'public' "
                            + "and table_type = 'BASE TABLE' "
                            + "order by table_name"));
            assertEquals(EXPECTED_TABLES, tables,
                    "Live database audit failed: TABLE_INVENTORY");

            Set<String> ownershipConstraints = Set.copyOf(queryStrings(
                    connection,
                    "select conname from pg_constraint "
                            + "where connamespace = "
                            + "('public'::regnamespace) "
                            + "and conname like 'fk_%_owned_%' "
                            + "order by conname"));
            assertEquals(EXPECTED_OWNERSHIP_CONSTRAINTS,
                    ownershipConstraints,
                    "Live database audit failed: OWNERSHIP_CONSTRAINTS");

            String countFingerprint = tableCountFingerprint(
                    connection, tables);
            connection.rollback();

            System.out.println("DatabaseProduct: PASS");
            System.out.println("TLS: PASS");
            System.out.println("MigrationHistory: PASS");
            System.out.println("MigrationChecksums: PASS");
            System.out.println("PublicTables: PASS");
            System.out.println("OwnershipConstraints: PASS");
            System.out.println("DataCountFingerprint: " + countFingerprint);
        } catch (SQLException exception) {
            fail("Live database audit failed: CONNECTION");
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(),
                "Live database audit failed: CONFIGURATION");
        return value;
    }

    private void assertSafeJdbcUrl(String url) {
        assertTrue(url.startsWith("jdbc:postgresql://"),
                "Live database audit failed: JDBC_URL");
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            assertTrue(uri.getUserInfo() == null,
                    "Live database audit failed: CREDENTIAL_SEPARATION");
            String query = uri.getRawQuery() == null
                    ? ""
                    : uri.getRawQuery().toLowerCase();
            assertTrue(!query.matches(
                            ".*(^|&)(user|password)=.*"),
                    "Live database audit failed: CREDENTIAL_SEPARATION");
            assertTrue(query.matches(
                            ".*(^|&)sslmode=(require|verify-ca|verify-full)"
                                    + "(&.*|$)"),
                    "Live database audit failed: TLS_CONFIGURATION");
        } catch (IllegalArgumentException exception) {
            fail("Live database audit failed: JDBC_URL");
        }
    }

    private void assertTlsEndpoint(String jdbcUrl) {
        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            fail("Live database audit failed: JDBC_URL");
            return;
        }
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(
                    uri.getHost(), port), 15_000);
            socket.setSoTimeout(15_000);

            DataOutputStream output = new DataOutputStream(
                    socket.getOutputStream());
            output.writeInt(8);
            output.writeInt(80_877_103);
            output.flush();

            DataInputStream input = new DataInputStream(
                    socket.getInputStream());
            assertEquals('S', input.readUnsignedByte(),
                    "Live database audit failed: TLS_NEGOTIATION");

            SSLSocketFactory factory = (SSLSocketFactory)
                    SSLSocketFactory.getDefault();
            try (SSLSocket tlsSocket = (SSLSocket) factory.createSocket(
                    socket, uri.getHost(), port, true)) {
                SSLParameters parameters = tlsSocket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tlsSocket.setSSLParameters(parameters);
                tlsSocket.startHandshake();
                assertTrue(tlsSocket.getSession().isValid(),
                        "Live database audit failed: TLS_CERTIFICATE");
            }
        } catch (IOException exception) {
            fail("Live database audit failed: TLS_CONNECTION");
        }
    }

    private int queryInt(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Expected one result row.");
            }
            return result.getInt(1);
        }
    }

    private List<String> queryStrings(Connection connection, String sql)
            throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        }
        return values;
    }

    private String tableCountFingerprint(Connection connection,
            Set<String> tables) throws SQLException {
        StringBuilder counts = new StringBuilder();
        for (String table : tables.stream().sorted().toList()) {
            if (!table.matches("[a-z_]+")) {
                throw new SQLException("Unexpected table name.");
            }
            counts.append(table)
                    .append('=')
                    .append(queryInt(connection,
                            "select count(*) from " + table))
                    .append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(counts.toString().getBytes(
                            java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 must be available.", exception);
        }
    }
}
