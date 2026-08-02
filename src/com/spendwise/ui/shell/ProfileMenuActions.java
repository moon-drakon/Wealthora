package com.spendwise.ui.shell;

import java.util.Objects;

public record ProfileMenuActions(
        Runnable openMyFinance,
        Runnable openProfile,
        Runnable openSecurityAndSessions,
        Runnable switchAccount,
        Runnable openAdminConsole,
        Runnable signOut) {

    public ProfileMenuActions {
        Objects.requireNonNull(openMyFinance, "My Finance action is required.");
        Objects.requireNonNull(openProfile, "Profile action is required.");
        Objects.requireNonNull(openSecurityAndSessions,
                "Security action is required.");
        Objects.requireNonNull(switchAccount,
                "Switch-account action is required.");
        Objects.requireNonNull(signOut, "Sign-out action is required.");
    }
}
