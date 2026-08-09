package com.spendwise.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What an import would do, shown to the user before anything is written.
 */
public record ImportPreview(
        String sourceDescription,
        Optional<Instant> createdAt,
        Map<String, Integer> newRecords,
        Map<String, Integer> duplicateRecords,
        List<String> warnings) {

    public ImportPreview {
        sourceDescription = Objects.requireNonNull(
                sourceDescription, "Import source description is required.");
        createdAt = Objects.requireNonNull(
                createdAt, "Import creation time is required.");
        newRecords = Map.copyOf(Objects.requireNonNull(
                newRecords, "New record counts are required."));
        duplicateRecords = Map.copyOf(Objects.requireNonNull(
                duplicateRecords, "Duplicate record counts are required."));
        warnings = List.copyOf(Objects.requireNonNull(
                warnings, "Import warnings are required."));
    }

    public int totalNewRecords() {
        return newRecords.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalDuplicates() {
        return duplicateRecords.values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    public boolean isEmpty() {
        return totalNewRecords() == 0 && totalDuplicates() == 0;
    }

    /** A short, plain summary suitable for a confirmation dialog. */
    public String displayText() {
        StringBuilder text = new StringBuilder(sourceDescription);
        createdAt.ifPresent(instant ->
                text.append("\nCreated: ").append(instant));
        text.append("\n\nReady to import:\n");
        Map<String, Integer> ordered = ordered();
        if (ordered.isEmpty()) {
            text.append("  Nothing new was found.\n");
        }
        for (Map.Entry<String, Integer> entry : ordered.entrySet()) {
            text.append("  ").append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append('\n');
        }
        text.append("\nPossible duplicates that will be skipped: ")
                .append(totalDuplicates());
        if (!warnings.isEmpty()) {
            text.append("\n\nWarnings:\n");
            warnings.stream().limit(10).forEach(warning ->
                    text.append("  • ").append(warning).append('\n'));
            if (warnings.size() > 10) {
                text.append("  • and ").append(warnings.size() - 10)
                        .append(" more\n");
            }
        }
        return text.toString();
    }

    private Map<String, Integer> ordered() {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (String section : WorkspaceSections.ORDER) {
            int count = newRecords.getOrDefault(section, 0);
            if (count > 0) {
                result.put(section, count);
            }
        }
        return result;
    }
}
