package com.spendwise.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The outcome of a completed import. */
public record ImportReport(
        Map<String, Integer> imported,
        Map<String, Integer> skippedDuplicates,
        List<ImportIssue> failures,
        List<String> warnings,
        Optional<Path> safetyBackup) {

    public ImportReport {
        imported = Map.copyOf(Objects.requireNonNull(
                imported, "Imported counts are required."));
        skippedDuplicates = Map.copyOf(Objects.requireNonNull(
                skippedDuplicates, "Skipped counts are required."));
        failures = List.copyOf(Objects.requireNonNull(
                failures, "Import failures are required."));
        warnings = List.copyOf(Objects.requireNonNull(
                warnings, "Import warnings are required."));
        safetyBackup = Objects.requireNonNull(
                safetyBackup, "Safety backup result is required.")
                .map(path -> path.toAbsolutePath().normalize());
    }

    public int totalImported() {
        return total(imported);
    }

    public int totalSkipped() {
        return total(skippedDuplicates);
    }

    public int totalFailed() {
        return failures.size();
    }

    /** A short, plain summary suitable for a completion dialog. */
    public String displayText() {
        StringBuilder text = new StringBuilder("Imported: ")
                .append(totalImported())
                .append("\nSkipped duplicates: ").append(totalSkipped())
                .append("\nFailed: ").append(totalFailed())
                .append("\nWarnings: ").append(warnings.size());
        for (String section : WorkspaceSections.ORDER) {
            int count = imported.getOrDefault(section, 0);
            if (count > 0) {
                text.append("\n  ").append(section).append(": ").append(count);
            }
        }
        if (!failures.isEmpty()) {
            text.append("\n\nNot imported:\n");
            failures.stream().limit(10).forEach(issue ->
                    text.append("  • ").append(issue).append('\n'));
            if (failures.size() > 10) {
                text.append("  • and ").append(failures.size() - 10)
                        .append(" more\n");
            }
        }
        if (!warnings.isEmpty()) {
            text.append("\nWarnings:\n");
            warnings.stream().limit(10).forEach(warning ->
                    text.append("  • ").append(warning).append('\n'));
            if (warnings.size() > 10) {
                text.append("  • and ").append(warnings.size() - 10)
                        .append(" more\n");
            }
        }
        safetyBackup.ifPresent(path ->
                text.append("\nSafety backup: ").append(path.getFileName()));
        return text.toString();
    }

    /** A full report that can be saved next to the imported file. */
    public String reportText(String sourceDescription) {
        StringBuilder text = new StringBuilder("Wealthora import report\n")
                .append("Source: ").append(sourceDescription).append("\n\n")
                .append(displayText()).append('\n');
        if (failures.size() > 10) {
            text.append("\nComplete list of records that were not imported:\n");
            failures.forEach(issue ->
                    text.append("  • ").append(issue).append('\n'));
        }
        if (warnings.size() > 10) {
            text.append("\nComplete list of warnings:\n");
            warnings.forEach(warning ->
                    text.append("  • ").append(warning).append('\n'));
        }
        return text.toString();
    }

    private static int total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
