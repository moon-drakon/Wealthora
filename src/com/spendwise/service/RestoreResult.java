package com.spendwise.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RestoreResult(
        List<String> restoredFiles, Optional<Path> safetyBackup) {

    public RestoreResult {
        restoredFiles = List.copyOf(Objects.requireNonNull(
                restoredFiles, "Restored filenames are required."));
        safetyBackup = Objects.requireNonNull(
                safetyBackup, "Safety backup result is required.")
                .map(path -> path.toAbsolutePath().normalize());
    }
}
