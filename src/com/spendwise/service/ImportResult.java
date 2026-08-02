package com.spendwise.service;

import java.nio.file.Path;
import java.util.Objects;

public record ImportResult(
        String recordType, int importedCount, Path safetyBackup) {
    public ImportResult {
        if (recordType == null || recordType.isBlank() || importedCount < 0) {
            throw new IllegalArgumentException("Invalid import result.");
        }
        Objects.requireNonNull(safetyBackup);
    }
}
