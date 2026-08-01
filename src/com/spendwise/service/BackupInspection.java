package com.spendwise.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record BackupInspection(
        Path source, Instant createdAt, List<String> includedFiles) {

    public BackupInspection {
        source = Objects.requireNonNull(source, "Backup source is required.")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(createdAt, "Backup creation time is required.");
        includedFiles = List.copyOf(Objects.requireNonNull(
                includedFiles, "Included backup files are required."));
    }
}
