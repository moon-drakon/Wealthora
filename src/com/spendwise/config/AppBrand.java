package com.spendwise.config;

/** Central source for user-visible product identity. */
public final class AppBrand {

    public static final String APP_NAME = "Wealthora";
    public static final String APP_VERSION = "1.2.3";
    public static final String TAGLINE = "Take Control of Every Taka.";
    public static final String DESCRIPTION =
            "A Smart Personal Finance Management System";
    public static final String NSU_EMAIL_SUBTITLE =
            "Smart Finance Management for NSU Students";
    public static final String WINDOW_TITLE = APP_NAME + " — " + TAGLINE;
    public static final String BACKUP_FILE_NAME = APP_NAME + "-backup.zip";
    public static final String BACKUP_FORMAT = APP_NAME + "Backup";
    public static final String JSON_BACKUP_FILE_NAME = APP_NAME + "-backup.json";

    /** Accepted only when reading backups produced before the rebrand. */
    public static final String LEGACY_BACKUP_APPLICATION =
            "SpendWise Expense Tracker";

    private AppBrand() {
    }
}
