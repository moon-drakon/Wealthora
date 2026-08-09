package com.spendwise.imports;

import java.util.List;
import java.util.Objects;

/**
 * What a selected file actually turned out to be.
 *
 * <p>Detection reads the file's own bytes. A name or extension is never enough
 * to decide a format, so a renamed file is still handled correctly and an
 * unexpected file is reported instead of being parsed.
 */
public record ForeignBackupFormat(
        Kind kind, String description, List<String> notes) {

    public enum Kind {
        /** A Money Manager backup: a database this app knows how to read. */
        MONEY_MANAGER_DATABASE,
        /** A database, but not one with Money Manager's tables. */
        UNKNOWN_DATABASE,
        /** A ZIP container; its useful entry, if any, is named in the notes. */
        ZIP_ARCHIVE,
        /** Delimited text that may be an export from another app. */
        DELIMITED_TEXT,
        /** Readable, but nothing this app can map. */
        UNSUPPORTED
    }

    public ForeignBackupFormat {
        kind = Objects.requireNonNull(kind, "Format kind is required.");
        description = Objects.requireNonNull(
                description, "Format description is required.");
        notes = List.copyOf(Objects.requireNonNull(
                notes, "Format notes are required."));
    }

    public boolean isImportable() {
        return kind == Kind.MONEY_MANAGER_DATABASE;
    }
}
