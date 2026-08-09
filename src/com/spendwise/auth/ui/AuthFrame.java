package com.spendwise.auth.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.auth.AuthService;
import com.spendwise.auth.OwnerSetupService;
import com.spendwise.auth.LocalAccountService;
import com.spendwise.auth.SessionManager;
import com.spendwise.auth.UserSession;
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
    private static final String SIGN_UP = "sign-up";
    private static final String VERIFICATION = "verification";
    private static final String FORGOT_PASSWORD = "forgot-password";
    private static final String OFFLINE_RECOVERY = "offline-recovery";
    private static final String EMAIL_RESET = "email-reset";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final AuthenticatedProfilePanel profilePanel;
    private final OwnerSetupPanel ownerSetupPanel;
    private final VerificationPanel verificationPanel;
    private final Consumer<UserSession> authenticatedListener;
    private final boolean localAccountUi;

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
        localAccountUi = requiredService instanceof LocalAccountService;
        profilePanel = new AuthenticatedProfilePanel(
                requiredService, requiredSessions, this);
        content.add(scroll(new SignInPanel(
                requiredService, requiredSessions, this)), SIGN_IN);
        content.add(scroll(new SignUpPanel(
                requiredService, requiredSessions, this)), SIGN_UP);
        verificationPanel = new VerificationPanel(requiredService, this);
        content.add(scroll(verificationPanel), VERIFICATION);
        if (localAccountUi) {
            content.add(scroll(new RecoveryChoicePanel(this)), FORGOT_PASSWORD);
            content.add(scroll(new ForgotPasswordPanel(
                    requiredService, this)), OFFLINE_RECOVERY);
            content.add(scroll(new EmailPasswordResetPanel(
                    requiredService, this)), EMAIL_RESET);
        }
        content.add(scroll(profilePanel), PROFILE);
        ownerSetupPanel = requiredService instanceof OwnerSetupService setup
                ? new OwnerSetupPanel(setup, requiredSessions, this) : null;
        if (ownerSetupPanel != null) {
            content.add(scroll(ownerSetupPanel), OWNER_SETUP);
        }
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(680, 820);
        setMinimumSize(new Dimension(520, 620));
        setLocationRelativeTo(null);
        if (requiredService instanceof OwnerSetupService setup
                && setup.isOwnerSetupRequired()) {
            showOwnerSetup();
        } else {
            showSignIn();
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
        cards.show(content, SIGN_UP);
    }

    @Override
    public void showVerification(String email) {
        verificationPanel.setEmail(email);
        cards.show(content, VERIFICATION);
    }

    @Override
    public void showRegistrationVerification(
            com.spendwise.auth.otp.EmailOtpChallenge challenge) {
        verificationPanel.setChallenge(challenge);
        cards.show(content, VERIFICATION);
    }

    @Override
    public void showForgotPassword() {
        cards.show(content, localAccountUi ? FORGOT_PASSWORD : SIGN_IN);
    }

    @Override
    public void showEmailPasswordReset() {
        cards.show(content, localAccountUi ? EMAIL_RESET : SIGN_IN);
    }

    @Override
    public void showOfflineRecovery() {
        cards.show(content, localAccountUi ? OFFLINE_RECOVERY : SIGN_IN);
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
