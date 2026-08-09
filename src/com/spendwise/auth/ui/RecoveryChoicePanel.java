package com.spendwise.auth.ui;

import java.util.Objects;

/** Lets a local user explicitly choose online OTP or offline recovery. */
public final class RecoveryChoicePanel extends AuthFormPanel {

    public RecoveryChoicePanel(AuthNavigator navigator) {
        super("Forgot Password",
                "Choose the recovery method appropriate for this local account.");
        AuthNavigator required = Objects.requireNonNull(navigator);
        addWide(sectionHeading("Email OTP Reset",
                "Uses the internet only to send and verify a one-time code. Passwords never leave this project."));
        addWide(primary("Use Email OTP", required::showEmailPasswordReset));
        addWide(sectionHeading("Offline Recovery",
                "Uses the protected recovery question stored on this computer and makes no network request."));
        addWide(secondary("Use Offline Recovery", required::showOfflineRecovery));
        addWide(buttonRow(secondary("Back to Sign In", required::showSignIn)));
    }
}
