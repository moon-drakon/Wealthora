package com.spendwise.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Holds the exclusive lock protecting one portable Wealthora data folder. */
public final class ProjectDataLock implements AutoCloseable {

    private static final String LOCK_NAME = ".wealthora.lock";
    private final FileChannel channel;
    private final FileLock lock;

    private ProjectDataLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static ProjectDataLock acquire(Path dataRoot) {
        Path root = dataRoot.toAbsolutePath().normalize();
        FileChannel channel = null;
        try {
            Files.createDirectories(root);
            requireWritableDirectory(root);
            Path lockPath = root.resolve(LOCK_NAME);
            channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new IllegalStateException(
                        "This portable Wealthora data folder is already in use. "
                        + "Close the other Wealthora window before continuing.");
            }
            byte[] marker = "Wealthora portable data lock\n"
                    .getBytes(StandardCharsets.UTF_8);
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(marker));
            channel.force(true);
            return new ProjectDataLock(channel, lock);
        } catch (IOException | SecurityException exception) {
            closeQuietly(channel);
            throw new IllegalStateException(
                    "Wealthora cannot write to its project-local data folder. "
                    + "Move the project to writable media or update folder permissions.",
                    exception);
        }
    }

    public static void ensureLayout(Path dataRoot) {
        try {
            Files.createDirectories(dataRoot.resolve("auth"));
            Files.createDirectories(dataRoot.resolve("users"));
            Files.createDirectories(dataRoot.resolve("backups"));
            Files.createDirectories(dataRoot.resolve("settings"));
            Files.createDirectories(dataRoot.resolve("presentation"));
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException(
                    "Wealthora could not initialize its portable data folders.",
                    exception);
        }
    }

    private static void requireWritableDirectory(Path directory)
            throws IOException {
        Path probe = Files.createTempFile(directory, ".wealthora-write-", ".tmp");
        try {
            Files.writeString(probe, "writable", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    @Override
    public void close() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
            // The process is ending; closing the channel also releases the lock.
        } finally {
            closeQuietly(channel);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best effort during startup failure or shutdown.
        }
    }
}
