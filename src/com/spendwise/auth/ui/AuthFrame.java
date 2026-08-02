package com.spendwise.auth.ui;

import com.spendwise.auth.AuthService;
import com.spendwise.auth.BackendAuthService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UnconfiguredAuthApiClient;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class AuthFrame extends JFrame implements AuthNavigator {

    private static final String SIGN_IN = "sign-in";
    private static final String SIGN_UP = "sign-up";
    private static final String VERIFY = "verify";
    private static final String FORGOT = "forgot";
    private static final String RESET = "reset";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final VerificationPanel verificationPanel;

    public AuthFrame(AuthService authService, SessionManager sessionManager) {
        super("SpendWise Authentication");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "AuthFrame must be created on the Event Dispatch Thread.");
        }
        AuthService requiredService = Objects.requireNonNull(authService);
        SessionManager requiredSessions = Objects.requireNonNull(sessionManager);
        verificationPanel = new VerificationPanel(
                requiredService, requiredSessions, this);
        content.add(new SignInPanel(
                requiredService, requiredSessions, this), SIGN_IN);
        content.add(new SignUpPanel(requiredService, this), SIGN_UP);
        content.add(verificationPanel, VERIFY);
        content.add(new ForgotPasswordPanel(requiredService, this), FORGOT);
        content.add(new ResetPasswordPanel(requiredService, this), RESET);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 720);
        setMinimumSize(new Dimension(500, 620));
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
}
