package com.spendwise.config;

import java.nio.file.Path;
import java.util.Locale;

public final class AppPaths {

    private static final String APPLICATION_DIRECTORY = "SpendWiseExpenseTracker";

    private AppPaths() {
    }

    public static Path getExpenseCsvPath() {
        return resolveDataFilePath(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home"),
                "expenses.csv");
    }

    public static Path getBudgetCsvPath() {
        return resolveDataFilePath(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home"),
                "budgets.csv");
    }

    public static Path getCategoryCsvPath() {
        return resolveDataFilePath(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home"),
                "categories.csv");
    }

    public static Path getAccountCsvPath() {
        return currentDataPath("accounts.csv");
    }

    public static Path getIncomeCsvPath() {
        return currentDataPath("income.csv");
    }

    public static Path getTransferCsvPath() {
        return currentDataPath("transfers.csv");
    }

    public static Path getRecurringCsvPath() {
        return currentDataPath("recurring.csv");
    }

    public static Path getAccountSettingsCsvPath() {
        return currentDataPath("account-settings.csv");
    }

    static Path resolveExpenseCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "expenses.csv");
    }

    static Path resolveBudgetCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "budgets.csv");
    }

    static Path resolveCategoryCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "categories.csv");
    }

    static Path resolveAccountCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "accounts.csv");
    }

    static Path resolveIncomeCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "income.csv");
    }

    static Path resolveTransferCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "transfers.csv");
    }

    static Path resolveRecurringCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "recurring.csv");
    }

    static Path resolveAccountSettingsCsvPath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome) {
        return resolveDataFilePath(
                operatingSystemName,
                localAppData,
                xdgDataHome,
                userHome,
                "account-settings.csv");
    }

    private static Path currentDataPath(String fileName) {
        return resolveDataFilePath(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home"),
                fileName);
    }

    private static Path resolveDataFilePath(
            String operatingSystemName,
            String localAppData,
            String xdgDataHome,
            String userHome,
            String fileName) {
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
                .resolve(fileName)
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
