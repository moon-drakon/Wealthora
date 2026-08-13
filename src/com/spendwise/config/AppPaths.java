package com.spendwise.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves Wealthora's portable project and data locations.
 *
 * <p>The project directory is the portable unit. Runtime data is always below
 * {@code <project-root>/data}; the operating-system application-data folder is
 * exposed only as a possible migration source for older releases.
 */
public final class AppPaths {

    public static final String PROJECT_ROOT_PROPERTY = "wealthora.project.root";
    public static final String PROJECT_ROOT_ENVIRONMENT =
            "WEALTHORA_PROJECT_ROOT";
    private static final String LEGACY_APPLICATION_DIRECTORY =
            "SpendWiseExpenseTracker";
    private static volatile Path cachedProjectRoot;
    private static volatile Path activeDataDirectory;

    private AppPaths() {
    }

    public static Path getProjectRoot() {
        Path cached = cachedProjectRoot;
        if (cached != null) {
            return cached;
        }
        synchronized (AppPaths.class) {
            if (cachedProjectRoot == null) {
                cachedProjectRoot = resolveProjectRoot(
                        Path.of(System.getProperty("user.dir", ".")),
                        codeLocation(), configuredProjectRoot());
            }
            return cachedProjectRoot;
        }
    }

    public static Path getDataRootDirectory() {
        return getProjectRoot().resolve("data").toAbsolutePath().normalize();
    }

    public static Path getAuthenticationDirectory() {
        return getDataRootDirectory().resolve("auth");
    }

    public static Path getBackupDirectory() {
        return getDataRootDirectory().resolve("backups");
    }

    public static Path getSettingsDirectory() {
        return getDataRootDirectory().resolve("settings");
    }

    public static Path getPresentationDirectory() {
        return getDataRootDirectory().resolve("presentation");
    }

    /**
     * Compatibility name for finance files stored directly below data/ by
     * early project-local builds. It is not an AppData fallback.
     */
    public static Path getLegacyDataDirectory() {
        return getDataRootDirectory();
    }

    /** Returns the old OS-specific root only for an explicit migration flow. */
    public static Path getLegacyApplicationRoot() {
        return legacyApplicationRoot(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home"));
    }

    /** Returns the finance directory used by pre-portable releases. */
    public static Path getLegacyAppDataDirectory() {
        return getLegacyApplicationRoot().resolve("data")
                .toAbsolutePath().normalize();
    }

    public static Path getUserDataDirectory(String trustedUserIdentifier) {
        String identifier = requiredValue(trustedUserIdentifier,
                "A trusted user identifier is required.");
        if (!identifier.matches("[A-Za-z0-9_-]{3,80}")) {
            throw new IllegalArgumentException(
                    "The trusted user identifier is invalid.");
        }
        return getDataRootDirectory().resolve("users").resolve(identifier)
                .toAbsolutePath().normalize();
    }

    public static synchronized void activateUserDataDirectory(
            String trustedUserIdentifier) {
        activeDataDirectory = getUserDataDirectory(trustedUserIdentifier);
    }

    public static synchronized void clearUserDataDirectory() {
        activeDataDirectory = null;
    }

    public static Path getDataDirectory() {
        Path active = activeDataDirectory;
        return active == null ? getLegacyDataDirectory() : active;
    }

    public static Path getExpenseCsvPath() {
        return currentDataPath("expenses.csv");
    }

    public static Path getBudgetCsvPath() {
        return currentDataPath("budgets.csv");
    }

    public static Path getCategoryCsvPath() {
        return currentDataPath("categories.csv");
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

    public static Path getPaymentCardCsvPath() {
        return currentDataPath("cards.csv");
    }

    public static Path getCurrencySettingsCsvPath() {
        return currentDataPath("currency-settings.csv");
    }

    public static Path getBudgetPlanCsvPath() {
        return currentDataPath("budget-plans.csv");
    }

    public static Path getSavingsGoalCsvPath() {
        return currentDataPath("savings-goals.csv");
    }

    public static Path getDebtCsvPath() {
        return currentDataPath("debts.csv");
    }

    static Path resolveProjectRoot(
            Path workingDirectory, Path codeLocation, String configuredRoot) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            Path explicit = Path.of(configuredRoot.strip())
                    .toAbsolutePath().normalize();
            requireApplicationMarkers(explicit,
                    "The configured Wealthora application root is invalid.");
            return explicit;
        }
        Path fromWorkingDirectory = findProjectRoot(workingDirectory);
        if (fromWorkingDirectory != null) {
            return fromWorkingDirectory;
        }
        Path fromCode = findProjectRoot(codeLocation);
        if (fromCode != null) {
            return fromCode;
        }
        throw new IllegalStateException(
                "Wealthora could not locate its application root. Run from "
                + "the extracted release or project folder, or set "
                + "WEALTHORA_PROJECT_ROOT to that folder.");
    }

    private static Path findProjectRoot(Path startingPoint) {
        if (startingPoint == null) {
            return null;
        }
        Path candidate = startingPoint.toAbsolutePath().normalize();
        if (Files.isRegularFile(candidate)) {
            candidate = candidate.getParent();
        }
        while (candidate != null) {
            if (hasApplicationMarkers(candidate)) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static boolean hasApplicationMarkers(Path directory) {
        return hasSourceProjectMarkers(directory)
                || hasPackagedReleaseMarkers(directory);
    }

    private static boolean hasSourceProjectMarkers(Path directory) {
        return Files.isRegularFile(directory.resolve("build.xml"))
                && Files.isRegularFile(
                        directory.resolve("nbproject").resolve("project.xml"));
    }

    private static boolean hasPackagedReleaseMarkers(Path directory) {
        return Files.isRegularFile(directory.resolve("Start Wealthora.cmd"))
                && Files.isRegularFile(directory.resolve("dist")
                        .resolve("Wealthora.jar"));
    }

    private static void requireApplicationMarkers(
            Path directory, String message) {
        if (!hasApplicationMarkers(directory)) {
            throw new IllegalStateException(message);
        }
    }

    private static String configuredProjectRoot() {
        String property = System.getProperty(PROJECT_ROOT_PROPERTY);
        return property == null || property.isBlank()
                ? System.getenv(PROJECT_ROOT_ENVIRONMENT) : property;
    }

    private static Path codeLocation() {
        try {
            URI location = AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            return Path.of(location);
        } catch (URISyntaxException | RuntimeException exception) {
            return null;
        }
    }

    private static Path currentDataPath(String fileName) {
        return getDataDirectory().resolve(fileName)
                .toAbsolutePath().normalize();
    }

    static Path resolveExpenseCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "expenses.csv");
    }

    static Path resolveBudgetCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "budgets.csv");
    }

    static Path resolveCategoryCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "categories.csv");
    }

    static Path resolveAccountCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "accounts.csv");
    }

    static Path resolveIncomeCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "income.csv");
    }

    static Path resolveTransferCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "transfers.csv");
    }

    static Path resolveRecurringCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "recurring.csv");
    }

    static Path resolveAccountSettingsCsvPath(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        return legacyDataFile(operatingSystemName, localAppData,
                xdgDataHome, userHome, "account-settings.csv");
    }

    private static Path legacyDataFile(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome, String fileName) {
        return legacyApplicationRoot(operatingSystemName, localAppData,
                xdgDataHome, userHome).resolve("data").resolve(fileName)
                .toAbsolutePath().normalize();
    }

    static Path legacyApplicationRoot(
            String operatingSystemName, String localAppData,
            String xdgDataHome, String userHome) {
        String operatingSystem = requiredValue(operatingSystemName,
                "Operating-system information is unavailable.")
                .toLowerCase(Locale.ROOT);
        String home = optionalValue(userHome);
        Path root;
        if (operatingSystem.startsWith("windows")) {
            String local = optionalValue(localAppData);
            root = local == null
                    ? requiredHome(home).resolve("AppData").resolve("Local")
                    : Path.of(local);
        } else if (operatingSystem.contains("mac")
                || operatingSystem.contains("darwin")) {
            root = requiredHome(home).resolve("Library")
                    .resolve("Application Support");
        } else {
            String xdg = optionalValue(xdgDataHome);
            root = xdg == null
                    ? requiredHome(home).resolve(".local").resolve("share")
                    : Path.of(xdg);
        }
        return root.resolve(LEGACY_APPLICATION_DIRECTORY)
                .toAbsolutePath().normalize();
    }

    private static Path requiredHome(String userHome) {
        if (userHome == null) {
            throw new IllegalStateException(
                    "The user home directory is required to locate legacy data.");
        }
        return Path.of(userHome);
    }

    private static String requiredValue(String value, String message) {
        String normalized = optionalValue(value);
        if (normalized == null) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }

    private static String optionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static synchronized void resetForTests() {
        cachedProjectRoot = null;
        activeDataDirectory = null;
    }
}
