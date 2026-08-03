package com.spendwise.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MigrationPreviewService {

    private final Path localDataDirectory;

    public MigrationPreviewService(Path localDataDirectory) {
        this.localDataDirectory = Objects.requireNonNull(
                localDataDirectory, "Local data directory is required.")
                .toAbsolutePath().normalize();
    }

    public MigrationPreview preview() {
        if (!Files.isDirectory(localDataDirectory)) {
            return new MigrationPreview(localDataDirectory, List.of(), 0L);
        }
        try (var paths = Files.walk(localDataDirectory)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString)).toList();
            long bytes = 0L;
            for (Path file : files) bytes = Math.addExact(bytes, Files.size(file));
            return new MigrationPreview(localDataDirectory, files.stream()
                    .map(localDataDirectory::relativize)
                    .map(Path::toString).toList(), bytes);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalStateException(
                    "The local migration preview could not be prepared.",
                    exception);
        }
    }

    public record MigrationPreview(
            Path localDataDirectory, List<String> files, long totalBytes) {

        public MigrationPreview {
            files = List.copyOf(files);
        }

        public String displayText() {
            StringBuilder result = new StringBuilder()
                    .append("Migration preview only — nothing will be uploaded.\n\n")
                    .append("Local workspace: ").append(localDataDirectory)
                    .append("\nFiles: ").append(files.size())
                    .append("\nTotal bytes: ").append(totalBytes);
            if (!files.isEmpty()) {
                result.append("\n\nFiles reviewed:\n");
                files.stream().limit(30).forEach(file -> result
                        .append(" • ").append(file).append('\n'));
                if (files.size() > 30) result.append(" • … and ")
                        .append(files.size() - 30).append(" more\n");
            }
            return result.append("\nAutomatic migration and synchronization "
                    + "are not enabled.").toString();
        }
    }
}
