package com.spendwise.service;

import com.spendwise.repository.RepositoryException;
import com.spendwise.validation.ValidationException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class SafeFileSupport {

    private SafeFileSupport() {
    }

    static void write(
            Path destination,
            byte[] content,
            boolean allowOverwrite,
            String temporaryPrefix,
            String dataName) {
        Path normalized = destination.toAbsolutePath().normalize();
        if (!allowOverwrite && Files.exists(normalized)) {
            throw new ValidationException(
                    dataName + " destination already exists.");
        }
        Path temporary = createTemporary(
                normalized, content, temporaryPrefix, dataName);
        try {
            move(temporary, normalized, allowOverwrite);
            temporary = null;
        } catch (IOException | SecurityException exception) {
            throw new RepositoryException(
                    "Could not save " + dataName + ".", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    static Path createTemporary(
            Path destination,
            byte[] content,
            String temporaryPrefix,
            String dataName) {
        Path normalized = destination.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new ValidationException(
                    dataName + " destination must have a parent directory.");
        }
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, temporaryPrefix, ".tmp");
            Files.write(
                    temporary,
                    content,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            return temporary;
        } catch (IOException | SecurityException exception) {
            deleteTemporary(temporary);
            throw new RepositoryException(
                    "Could not stage " + dataName + ".", exception);
        }
    }

    static void move(
            Path temporary, Path destination, boolean allowOverwrite)
            throws IOException {
        StandardCopyOption[] atomicOptions = allowOverwrite
                ? new StandardCopyOption[]{
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        StandardCopyOption[] fallbackOptions = allowOverwrite
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{};
        try {
            Files.move(temporary, destination, atomicOptions);
        } catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(temporary, destination, fallbackOptions);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(exception);
                throw fallbackFailure;
            }
        }
    }

    static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException | SecurityException ignored) {
            // The original operation reports the actionable failure.
        }
    }
}
