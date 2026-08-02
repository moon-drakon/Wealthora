package com.spendwise.auth.audit;

import com.spendwise.auth.AuthException;
import com.spendwise.repository.CsvFileSupport;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CsvAuditRepository implements AuditRepository {

    private static final List<String> HEADER = List.of(
            "occurred_at", "actor_user_id", "action", "target_user_id",
            "outcome", "reason");

    private final Path csvPath;

    public CsvAuditRepository(Path csvPath) {
        this.csvPath = Objects.requireNonNull(
                csvPath, "Audit CSV path is required.")
                .toAbsolutePath().normalize();
    }

    @Override
    public synchronized void append(AuditEvent event) {
        AuditEvent required = Objects.requireNonNull(
                event, "Audit event is required.");
        List<AuditEvent> events = new ArrayList<>(findAll());
        events.add(required);
        write(events);
    }

    @Override
    public synchronized List<AuditEvent> findAll() {
        var content = CsvFileSupport.read(csvPath, "authentication audit");
        if (content.isEmpty()) return List.of();
        List<List<String>> rows = CsvFileSupport.parse(
                content.get(), HEADER, "authentication audit");
        List<AuditEvent> events = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> fields = rows.get(index);
            if (fields.size() != HEADER.size()) {
                throw new AuthException("Authentication audit data is invalid.");
            }
            try {
                events.add(new AuditEvent(
                        Instant.parse(fields.get(0)), fields.get(1),
                        AuditAction.valueOf(fields.get(2)), fields.get(3),
                        fields.get(4), fields.get(5)));
            } catch (RuntimeException exception) {
                throw new AuthException(
                        "Authentication audit data is invalid.", exception);
            }
        }
        events.sort(Comparator.comparing(AuditEvent::occurredAt).reversed());
        return List.copyOf(events);
    }

    private void write(List<AuditEvent> events) {
        StringBuilder csv = new StringBuilder(String.join(",", HEADER))
                .append('\n');
        for (AuditEvent event : events) {
            appendRow(csv, event.occurredAt().toString(),
                    event.actorUserIdentifier(), event.action().name(),
                    event.targetUserIdentifier(), event.outcome(),
                    event.reason());
        }
        CsvFileSupport.write(csvPath, ".wealthora-audit-", csv.toString(),
                "authentication audit");
    }

    private static void appendRow(StringBuilder csv, String... fields) {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) csv.append(',');
            CsvFileSupport.appendField(csv, fields[index]);
        }
        csv.append('\n');
    }
}
