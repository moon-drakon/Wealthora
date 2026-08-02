package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JLabel;

public final class AuthenticatedProfilePanel extends AuthFormPanel {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuthNavigator navigator;
    private final JLabel avatar = new JLabel("?");
    private final JLabel welcome = new JLabel("Welcome");
    private final JLabel email = new JLabel(" ");
    private final JLabel provider = new JLabel(" ");
    private final JLabel badges = new JLabel(" ");

    public AuthenticatedProfilePanel(
            AuthService authService,
            SessionManager sessionManager,
            AuthNavigator navigator) {
        super("Profile", "Your verified authentication identity.");
        this.authService = Objects.requireNonNull(authService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.navigator = Objects.requireNonNull(navigator);
        avatar.getAccessibleContext().setAccessibleName("Profile initials");
        addWide(avatar);
        addWide(welcome);
        addWide(email);
        addWide(provider);
        addWide(badges);
        addWide(primary("Logout", this::logout));
    }

    public void setSession(UserSession session) {
        UserSession required = Objects.requireNonNull(session);
        avatar.setText(initials(required.getDisplayName()));
        welcome.setText("Welcome, " + required.getDisplayName());
        email.setText(required.getEmail());
        provider.setText("Authentication provider: "
                + required.getProvider().name().replace('_', ' '));
        badges.setText("<html>"
                + String.join("<br>", required.getUser().getProfileBadges())
                + "</html>");
    }

    private void logout() {
        try {
            authService.logout();
            sessionManager.clearSession();
            navigator.showSignIn();
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }

    private static String initials(String name) {
        StringBuilder result = new StringBuilder();
        for (String part : name.strip().split("\\s+")) {
            if (!part.isEmpty() && result.length() < 2) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return result.toString().toUpperCase(Locale.ROOT);
    }
}
