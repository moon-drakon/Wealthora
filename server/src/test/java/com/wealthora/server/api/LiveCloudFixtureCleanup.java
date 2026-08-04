package com.wealthora.server.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Removes only disposable users created by CloudFinanceSwingLiveTest.
 */
public final class LiveCloudFixtureCleanup {

    private static final Pattern TEST_EMAIL = Pattern.compile(
            "wealthora\\.swing\\.e2e\\.[a-f0-9]{32}"
                    + "(?:\\.isolated)?@northsouth\\.edu");

    private LiveCloudFixtureCleanup() {
    }

    public static void main(String[] arguments) throws Exception {
        Path repositoryRoot = requiredDirectory(
                "WEALTHORA_REPOSITORY_ROOT");
        Path fixtureFile = requiredExternalFile(
                "WEALTHORA_LIVE_FIXTURE_FILE", repositoryRoot);
        List<String> emails = Files.readAllLines(
                fixtureFile, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        require(!emails.isEmpty() && emails.size() <= 2,
                "The live fixture marker is invalid.");
        require(emails.stream().allMatch(
                email -> TEST_EMAIL.matcher(email).matches()),
                "Cleanup refused a non-fixture identity.");

        int deleted = 0;
        try (Connection connection = databaseConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String email : emails) {
                    deleted += deleteUser(connection, email);
                }
                connection.commit();
            } catch (Throwable failure) {
                connection.rollback();
                throw failure;
            }
        }
        int absent = emails.size() - deleted;
        boolean allowAbsent = Boolean.parseBoolean(System.getenv(
                "WEALTHORA_ALLOW_ABSENT_LIVE_FIXTURE"));
        require(allowAbsent || deleted == emails.size(),
                "Not every disposable fixture user was removed.");

        Path mailDirectory = optionalExternalDirectory(
                "WEALTHORA_DEV_MAIL_DIR", repositoryRoot);
        if (mailDirectory != null) {
            for (String email : emails) {
                String safeName = email.replaceAll(
                        "[^A-Za-z0-9._-]", "_");
                clearAndDelete(mailDirectory.resolve(safeName + ".txt"));
                clearAndDelete(
                        mailDirectory.resolve(safeName + ".reset.txt"));
            }
        }
        clearAndDelete(fixtureFile);
        System.out.println("DisposableCloudUsersRemoved: " + deleted);
        System.out.println("DisposableCloudUsersAbsent: " + absent);
        System.out.println("LiveCloudFixtureCleanup: PASS");
    }

    private static int deleteUser(Connection connection, String email)
            throws Exception {
        UUID userId = null;
        try (PreparedStatement find = connection.prepareStatement(
                "select id from users where email = ?")) {
            find.setString(1, email);
            try (ResultSet result = find.executeQuery()) {
                if (result.next()) {
                    userId = result.getObject(1, UUID.class);
                }
            }
        }
        if (userId == null) {
            return 0;
        }
        try (PreparedStatement audit = connection.prepareStatement(
                "delete from audit_logs where actor_user_id = ? "
                        + "or target_user_id = ?")) {
            audit.setObject(1, userId);
            audit.setObject(2, userId);
            audit.executeUpdate();
        }
        try (PreparedStatement attempts = connection.prepareStatement(
                "delete from login_attempts where user_id = ?")) {
            attempts.setObject(1, userId);
            attempts.executeUpdate();
        }
        try (PreparedStatement user = connection.prepareStatement(
                "delete from users where id = ?")) {
            user.setObject(1, userId);
            return user.executeUpdate();
        }
    }

    private static Connection databaseConnection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user",
                requiredEnvironment("DATABASE_USERNAME"));
        properties.setProperty("password",
                requiredEnvironment("DATABASE_PASSWORD"));
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");
        return DriverManager.getConnection(
                requiredEnvironment("DATABASE_URL"), properties);
    }

    private static Path requiredDirectory(String name) {
        Path path = Path.of(requiredEnvironment(name))
                .toAbsolutePath().normalize();
        require(Files.isDirectory(path),
                name + " must identify an existing directory.");
        return path;
    }

    private static Path requiredExternalFile(
            String name, Path repositoryRoot) {
        Path path = Path.of(requiredEnvironment(name))
                .toAbsolutePath().normalize();
        require(!path.startsWith(repositoryRoot)
                && Files.isRegularFile(path),
                name + " must identify an external regular file.");
        return path;
    }

    private static Path optionalExternalDirectory(
            String name, Path repositoryRoot) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        require(!path.startsWith(repositoryRoot)
                && Files.isDirectory(path),
                name + " must identify an external directory.");
        return path;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(),
                name + " is required for live fixture cleanup.");
        return value;
    }

    private static void clearAndDelete(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return;
        }
        long length = Files.size(path);
        byte[] zeros = new byte[(int) Math.min(length, 4096)];
        try (var output = Files.newOutputStream(path,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            long remaining = length;
            while (remaining > 0) {
                int count = (int) Math.min(remaining, zeros.length);
                output.write(zeros, 0, count);
                remaining -= count;
            }
        }
        Files.delete(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
