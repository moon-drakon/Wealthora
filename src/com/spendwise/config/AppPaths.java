package com.spendwise.config;

import java.nio.file.Path;
import java.util.Locale;

public final class AppPaths {

    private static final String APPLICATION_DIRECTORY = "SpendWiseExpenseTracker";

    private AppPaths() {
    }

    public static Path getExpenseCsvPath() {
        return resolveExpenseCsvPath(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home"));
    }

    static Path resolveExpenseCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        String normalizedOperatingSystem = requiredValue(
                operatingSystemName, "Operating-system information is unavailable.")
                .toLowerCase(Locale.ROOT);
        String normalizedHome = optionalValue(userHome);
        Path dataRoot;

        if (normalizedOperatingSystem.startsWith("windows")) {
            String normalizedLocalAppData = optionalValue(localAppData);
            dataRoot = normalizedLocalAppData != null
                    ? Path.of(normalizedLocalAppData)
                    : requiredHome(normalizedHome).resolve("AppData").resolve("Local");
        } else if (normalizedOperatingSystem.contains("mac")
                || normalizedOperatingSystem.contains("darwin")) {
            dataRoot = requiredHome(normalizedHome)
                    .resolve("Library")
                    .resolve("Application Support");
        } else {
            String normalizedXdgDataHome = optionalValue(xdgDataHome);
            dataRoot = normalizedXdgDataHome != null
                    ? Path.of(normalizedXdgDataHome)
                    : requiredHome(normalizedHome).resolve(".local").resolve("share");
        }

        return dataRoot
                .resolve(APPLICATION_DIRECTORY)
                .resolve("data")
                .resolve("expenses.csv")
                .toAbsolutePath()
                .normalize();
    }

    private static Path requiredHome(String userHome) {
        if (userHome == null) {
            throw new IllegalStateException(
                    "The user home directory is required to locate application data.");
        }
        return Path.of(userHome);
    }

    private static String requiredValue(String value, String message) {
        String normalizedValue = optionalValue(value);
        if (normalizedValue == null) {
            throw new IllegalStateException(message);
        }
        return normalizedValue;
    }

    private static String optionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }
}
