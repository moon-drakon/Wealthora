package com.spendwise.imports;

/** Raised when a selected file is not a database backup this app can read. */
public class SqliteFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SqliteFormatException(String message) {
        super(message);
    }

    public SqliteFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
