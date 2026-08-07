package com.spendwise.auth.local;

import com.spendwise.auth.AccountStatus;
import com.spendwise.auth.AuthException;
import com.spendwise.auth.AuthProvider;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.EmailAddressPolicy;
import com.spendwise.auth.UserRole;
import com.spendwise.repository.CsvFileSupport;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CsvLocalUserRepository implements LocalUserRepository {

    private static final List<String> LEGACY_HEADER = List.of(
            "user_id", "full_name", "email", "email_verified",
            "auth_provider", "google_subject_id", "password_hash", "roles",
            "account_status", "created_at", "updated_at", "last_login_at",
            "student_id", "preferred_theme", "preferred_currency",
            "failed_login_attempts", "locked_until");
    private static final List<String> HEADER = List.of(
            "user_id", "full_name", "email", "email_verified",
            "auth_provider", "google_subject_id", "password_hash", "roles",
            "account_status", "created_at", "updated_at", "last_login_at",
            "student_id", "preferred_theme", "preferred_currency",
            "failed_login_attempts", "locked_until", "recovery_question",
            "recovery_hint", "recovery_answer_hash");

    private final Path csvPath;

    public CsvLocalUserRepository(Path csvPath) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Local-user CSV path is required.")
                .toAbsolutePath().normalize();
    }

    @Override
    public synchronized List<LocalUserRecord> findAll() {
        return readAll();
    }

    @Override
    public synchronized Optional<LocalUserRecord> findById(String identifier) {
        return readAll().stream().filter(record -> record.user()
                .getUserIdentifier().equals(identifier)).findFirst();
    }

    @Override
    public synchronized Optional<LocalUserRecord> findByEmail(String email) {
        String normalized = EmailAddressPolicy.normalize(email);
        return readAll().stream().filter(record -> record.user()
                .getEmail().equals(normalized)).findFirst();
    }

    @Override
    public synchronized Optional<LocalUserRecord> findOwner() {
        return readAll().stream().filter(record -> record.user()
                .hasRole(UserRole.OWNER)).findFirst();
    }

    @Override
    public synchronized void save(LocalUserRecord record) {
        LocalUserRecord required = Objects.requireNonNull(
                record, "Local user record is required.");
        List<LocalUserRecord> records = new ArrayList<>(readAll());
        for (LocalUserRecord current : records) {
            if (!current.user().getUserIdentifier().equals(
                    required.user().getUserIdentifier())
                    && current.user().getEmail().equals(
                            required.user().getEmail())) {
                throw new AuthException("An account already uses this email.");
            }
            if (!current.user().getUserIdentifier().equals(
                    required.user().getUserIdentifier())
                    && current.user().hasRole(UserRole.OWNER)
                    && required.user().hasRole(UserRole.OWNER)) {
                throw new AuthException("A primary OWNER already exists.");
            }
        }
        records.removeIf(current -> current.user().getUserIdentifier().equals(
                required.user().getUserIdentifier()));
        records.add(required);
        records.sort(Comparator.comparing(recordValue ->
                recordValue.user().getCreatedAt()));
        writeAll(records);
    }

    private List<LocalUserRecord> readAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "local users");
        if (content.isEmpty()) return List.of();
        List<List<String>> rows;
        boolean legacy;
        try {
            rows = CsvFileSupport.parse(content.get(), HEADER, "local users");
            legacy = false;
        } catch (RuntimeException currentFormatFailure) {
            try {
                rows = CsvFileSupport.parse(
                        content.get(), LEGACY_HEADER, "local users");
                legacy = true;
            } catch (RuntimeException legacyFormatFailure) {
                currentFormatFailure.addSuppressed(legacyFormatFailure);
                throw currentFormatFailure;
            }
        }
        List<LocalUserRecord> records = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> fields = rows.get(index);
            int expectedFields = legacy ? LEGACY_HEADER.size() : HEADER.size();
            if (fields.size() != expectedFields) {
                throw new AuthException(
                        "Local-user record has an unsupported field count.");
            }
            records.add(parse(fields, legacy));
        }
        return List.copyOf(records);
    }

    private static LocalUserRecord parse(
            List<String> fields, boolean legacy) {
        try {
            AuthenticatedUser user = new AuthenticatedUser(
                    fields.get(0), fields.get(1), fields.get(2),
                    Boolean.parseBoolean(fields.get(3)),
                    AuthProvider.valueOf(fields.get(4)), fields.get(5),
                    AccountStatus.valueOf(fields.get(8)),
                    Instant.parse(fields.get(9)), Instant.parse(fields.get(10)),
                    optionalInstant(fields.get(11)), roles(fields.get(7)),
                    fields.get(12), fields.get(13), fields.get(14));
            return new LocalUserRecord(user, fields.get(6),
                    Integer.parseInt(fields.get(15)),
                    optionalInstant(fields.get(16)),
                    legacy ? "" : fields.get(17),
                    legacy ? "" : fields.get(18),
                    legacy ? "" : fields.get(19));
        } catch (RuntimeException exception) {
            throw new AuthException("Local-user data is invalid.", exception);
        }
    }

    private void writeAll(List<LocalUserRecord> records) {
        StringBuilder csv = new StringBuilder(String.join(",", HEADER))
                .append('\n');
        for (LocalUserRecord record : records) {
            AuthenticatedUser user = record.user();
            append(csv, user.getUserIdentifier(), user.getFullName(),
                    user.getEmail(), Boolean.toString(user.isEmailVerified()),
                    user.getPrimaryAuthProvider().name(),
                    user.getGoogleSubjectId(), record.passwordHash(),
                    roleText(user.getRoles()), user.getAccountStatus().name(),
                    user.getCreatedAt().toString(), user.getUpdatedAt().toString(),
                    instantText(user.getLastLoginAt()),
                    user.getStudentIdentifier(), user.getPreferredTheme(),
                    user.getPreferredCurrency(),
                    Integer.toString(record.failedLoginAttempts()),
                    instantText(record.lockedUntil()),
                    record.recoveryQuestion(), record.recoveryHint(),
                    record.recoveryAnswerHash());
        }
        CsvFileSupport.write(csvPath, ".wealthora-users-", csv.toString(),
                "local users");
    }

    private static void append(StringBuilder csv, String... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            CsvFileSupport.appendField(csv, values[index]);
        }
        csv.append('\n');
    }

    private static Set<UserRole> roles(String value) {
        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String role : value.split("\\|")) {
            if (!role.isBlank()) roles.add(UserRole.valueOf(role));
        }
        return Set.copyOf(roles);
    }

    private static String roleText(Set<UserRole> roles) {
        return roles.stream().sorted().map(Enum::name)
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String instantText(Instant value) {
        return value == null ? "" : value.toString();
    }

    private static Instant optionalInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
