package com.spendwise.auth.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.BackendAuthService;
import com.spendwise.auth.OwnerSetupService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
import com.spendwise.auth.UnconfiguredAuthApiClient;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public final class AuthFrame extends JFrame implements AuthNavigator {

    private static final String SIGN_IN = "sign-in";
    private static final String PROFILE = "profile";
    private static final String OWNER_SETUP = "owner-setup";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final AuthenticatedProfilePanel profilePanel;
    private final OwnerSetupPanel ownerSetupPanel;
    private final Consumer<UserSession> authenticatedListener;

    public AuthFrame(AuthService authService, SessionManager sessionManager) {
        this(authService, sessionManager, null);
    }

    public AuthFrame(
            AuthService authService,
            SessionManager sessionManager,
            Consumer<UserSession> authenticatedListener) {
        super(AppBrand.APP_NAME + " Authentication");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "AuthFrame must be created on the Event Dispatch Thread.");
        }
        AuthService requiredService = Objects.requireNonNull(authService);
        SessionManager requiredSessions = Objects.requireNonNull(sessionManager);
        this.authenticatedListener = authenticatedListener;
        profilePanel = new AuthenticatedProfilePanel(
                requiredService, requiredSessions, this);
        content.add(scroll(new SignInPanel(
                requiredService, requiredSessions, this)), SIGN_IN);
        content.add(scroll(profilePanel), PROFILE);
        ownerSetupPanel = requiredService instanceof OwnerSetupService setup
                ? new OwnerSetupPanel(setup, requiredSessions, this) : null;
        if (ownerSetupPanel != null) {
            content.add(scroll(ownerSetupPanel), OWNER_SETUP);
        }
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 780);
        setMinimumSize(new Dimension(520, 620));
        setLocationRelativeTo(null);
        if (requiredService instanceof OwnerSetupService setup
                && setup.isOwnerSetupRequired()) {
            showOwnerSetup();
        } else {
            showSignIn();
        }
    }

    public static void openUnconfiguredPreview() {
        Runnable open = () -> {
            AuthService service = new BackendAuthService(
                    new UnconfiguredAuthApiClient());
            AuthFrame frame = new AuthFrame(service, new SessionManager());
            frame.setVisible(true);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            open.run();
        } else {
            SwingUtilities.invokeLater(open);
        }
    }

    @Override
    public void showOwnerSetup() {
        if (ownerSetupPanel == null) {
            showSignIn();
            return;
        }
        ownerSetupPanel.reload();
        cards.show(content, OWNER_SETUP);
    }

    @Override
    public void showSignIn() {
        cards.show(content, SIGN_IN);
    }

    @Override
    public void showSignUp() {
        showSignIn();
    }

    @Override
    public void showVerification(String email) {
        showSignIn();
    }

    @Override
    public void showForgotPassword() {
        showSignIn();
    }

    @Override
    public void showResetPassword() {
        showSignIn();
    }

    @Override
    public void showAuthenticatedProfile(UserSession session) {
        if (authenticatedListener != null) {
            dispose();
            authenticatedListener.accept(session);
            return;
        }
        profilePanel.setSession(session);
        cards.show(content, PROFILE);
    }

    private static JScrollPane scroll(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }
}
