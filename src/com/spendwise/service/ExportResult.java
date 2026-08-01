package com.spendwise.service;

import java.nio.file.Path;
import java.util.Objects;

public record ExportResult(Path destination, int rowCount) {

    public ExportResult {
        destination = Objects.requireNonNull(
                destination, "Export destination is required.")
                .toAbsolutePath().normalize();
        if (rowCount < 0) {
            throw new IllegalArgumentException(
                    "Export row count cannot be negative.");
        }
    }
}
