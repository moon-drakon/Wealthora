package com.spendwise.auth.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.BackendAuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UnconfiguredAuthApiClient;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public final class AuthFrame extends JFrame implements AuthNavigator {

    private static final String SIGN_IN = "sign-in";
    private static final String SIGN_UP = "sign-up";
    private static final String VERIFY = "verify";
    private static final String FORGOT = "forgot";
    private static final String RESET = "reset";
    private static final String PROFILE = "profile";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final VerificationPanel verificationPanel;
    private final AuthenticatedProfilePanel profilePanel;

    public AuthFrame(AuthService authService, SessionManager sessionManager) {
        super(AppBrand.APP_NAME + " Authentication");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "AuthFrame must be created on the Event Dispatch Thread.");
        }
        AuthService requiredService = Objects.requireNonNull(authService);
        SessionManager requiredSessions = Objects.requireNonNull(sessionManager);
        verificationPanel = new VerificationPanel(requiredService, this);
        profilePanel = new AuthenticatedProfilePanel(
                requiredService, requiredSessions, this);
        content.add(scroll(new SignInPanel(
                requiredService, requiredSessions, this)), SIGN_IN);
        content.add(scroll(new SignUpPanel(
                requiredService, requiredSessions, this)), SIGN_UP);
        content.add(scroll(verificationPanel), VERIFY);
        content.add(scroll(new ForgotPasswordPanel(
                requiredService, this)), FORGOT);
        content.add(scroll(new ResetPasswordPanel(
                requiredService, this)), RESET);
        content.add(scroll(profilePanel), PROFILE);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 780);
        setMinimumSize(new Dimension(520, 620));
        setLocationRelativeTo(null);
        showSignIn();
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
    public void showSignIn() {
        cards.show(content, SIGN_IN);
    }

    @Override
    public void showSignUp() {
        cards.show(content, SIGN_UP);
    }

    @Override
    public void showVerification(String email) {
        verificationPanel.setEmail(email);
        cards.show(content, VERIFY);
    }

    @Override
    public void showForgotPassword() {
        cards.show(content, FORGOT);
    }

    @Override
    public void showResetPassword() {
        cards.show(content, RESET);
    }

    @Override
    public void showAuthenticatedProfile(
            com.spendwise.auth.UserSession session) {
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
