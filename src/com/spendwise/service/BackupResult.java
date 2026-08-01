package com.spendwise.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record BackupResult(
        Path destination, Instant createdAt, List<String> includedFiles) {

    public BackupResult {
        destination = Objects.requireNonNull(
                destination, "Backup destination is required.")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(createdAt, "Backup creation time is required.");
        includedFiles = List.copyOf(Objects.requireNonNull(
                includedFiles, "Included backup files are required."));
    }
}
