package com.spendwise.auth;

import com.spendwise.auth.otp.EmailOtpAccountService;
import com.spendwise.auth.otp.EmailOtpChallenge;
import com.spendwise.auth.ui.AuthNavigator;
import com.spendwise.auth.ui.EmailPasswordResetPanel;
import com.spendwise.auth.ui.ForgotPasswordPanel;
import com.spendwise.auth.ui.RecoveryChoicePanel;
import com.spendwise.auth.ui.SignInPanel;
import com.spendwise.auth.ui.SignUpPanel;
import com.spendwise.auth.ui.VerificationPanel;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class AuthClientFoundationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static int passed;

    private AuthClientFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        test("NSU email normalization", AuthClientFoundationTest::emailPolicy);
        test("password policy", AuthClientFoundationTest::passwordPolicy);
        test("verified local session", AuthClientFoundationTest::sessionPolicy);
        test("local authentication panels", AuthClientFoundationTest::panels);
        System.out.println("All " + passed
                + " local authentication foundation tests passed.");
    }

    private static void emailPolicy() {
        assertEquals("student@northsouth.edu",
                NsuEmailPolicy.requireInstitutionalEmail(
                        " Student@NorthSouth.edu "));
        assertTrue(NsuEmailPolicy.isInstitutionalEmail(
                "student@northsouth.edu"));
        assertFalse(NsuEmailPolicy.isInstitutionalEmail("student@gmail.com"));
        expect(AuthException.class, () ->
                NsuEmailPolicy.requireInstitutionalEmail(
                        "student@northsouth.edu.invalid"));
    }

    private static void passwordPolicy() {
        PasswordService passwords = new PasswordService();
        char[] value = "StudentPass1".toCharArray();
        String hash = passwords.hash(value);
        assertFalse(hash.contains("StudentPass1"));
        assertTrue(passwords.matches(value, hash));
        expect(AuthException.class, () ->
                passwords.requireStrong("short".toCharArray()));
    }

    private static void sessionPolicy() {
        AuthenticatedUser active = localUser(true, AccountStatus.ACTIVE);
        UserSession session = new UserSession(active, NOW);
        SessionManager sessions = new SessionManager();
        sessions.startSession(session);
        assertEquals(session, sessions.getCurrentSession().orElseThrow());
        sessions.clearSession();
        assertTrue(sessions.getCurrentSession().isEmpty());
        expect(AuthException.class, () -> new UserSession(
                localUser(false, AccountStatus.PENDING_EMAIL_VERIFICATION), NOW));
    }

    private static void panels() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalUiService service = new LocalUiService();
                SessionManager sessions = new SessionManager();
                AuthNavigator navigator = new NoOpNavigator();
                List<JPanel> panels = List.of(
                        new SignInPanel(service, sessions, navigator),
                        new SignUpPanel(service, sessions, navigator),
                        new VerificationPanel(service, navigator),
                        new RecoveryChoicePanel(navigator),
                        new ForgotPasswordPanel(service, navigator),
                        new EmailPasswordResetPanel(service, navigator));
                for (JPanel panel : panels) {
                    assertTrue(panel.getComponentCount() > 0);
                }
                List<String> signIn = componentText(panels.get(0));
                assertContains(signIn, "Local Sign In");
                assertContains(signIn, "Create Account");
                assertContains(signIn, "Forgot Password?");
                assertFalse(signIn.stream().anyMatch(value ->
                        value.toLowerCase(java.util.Locale.ROOT)
                                .contains("remote finance")));

                List<String> registration = componentText(panels.get(1));
                assertContains(registration, "Send Verification Code");
                assertContainsPart(registration,
                        "No account is created before successful verification");

                List<String> verification = componentText(panels.get(2));
                assertContains(verification, "Verify and Create Account");
                assertContains(verification, "Cancel Registration");

                List<String> recovery = componentText(panels.get(4));
                assertContains(recovery, "Find Account");
                assertContains(recovery, "Recovery Answer");

                List<String> emailReset = componentText(panels.get(5));
                assertContains(emailReset, "Send Reset Code");
                assertContains(emailReset,
                        "Verify Code and Reset Password");
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError("Authentication panel test failed.",
                    failure.get());
        }
    }

    private static AuthenticatedUser localUser(
            boolean verified, AccountStatus status) {
        return new AuthenticatedUser(
                "USER_LOCAL", "Student", "student@northsouth.edu",
                verified, AuthProvider.LOCAL, "", status, NOW, NOW);
    }

    private static List<String> componentText(Component component) {
        List<String> values = new ArrayList<>();
        if (component instanceof JLabel label && label.getText() != null) {
            values.add(label.getText());
        }
        if (component instanceof AbstractButton button
                && button.getText() != null) {
            values.add(button.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                values.addAll(componentText(child));
            }
        }
        return values;
    }

    private static void test(String name, ThrowingRunnable action)
            throws Exception {
        action.run();
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void expect(
            Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Unexpected exception", failure);
            }
        }
    }

    private static void assertContains(List<String> values, String expected) {
        if (!values.contains(expected)) {
            throw new AssertionError("Missing UI text: " + expected
                    + " in " + values);
        }
    }

    private static void assertContainsPart(
            List<String> values, String expected) {
        if (values.stream().noneMatch(value -> value.contains(expected))) {
            throw new AssertionError("Missing UI text containing: " + expected);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected " + expected + " but was " + actual);
        }
    }

    private static final class NoOpNavigator implements AuthNavigator {
        @Override public void showOwnerSetup() { }
        @Override public void showSignIn() { }
        @Override public void showSignUp() { }
        @Override public void showVerification(String email) { }
        @Override public void showForgotPassword() { }
        @Override public void showAuthenticatedProfile(UserSession session) { }
    }

    private static final class LocalUiService
            implements AuthService, LocalAccountService, EmailOtpAccountService {
        @Override public UserSession signInWithNsuEmail(
                String email, char[] password) { throw unsupported(); }
        @Override public void logout() { }
        @Override public AuthenticatedUser getCurrentUser() {
            throw unsupported();
        }
        @Override public PasswordRecoveryChallenge getPasswordRecoveryChallenge(
                String email) { throw unsupported(); }
        @Override public void resetPasswordWithRecovery(
                String email, char[] answer, char[] password,
                char[] confirmation) { throw unsupported(); }
        @Override public boolean hasPasswordRecovery(UserSession session) {
            return true;
        }
        @Override public void updatePasswordRecovery(
                UserSession session, char[] currentPassword,
                String question, String hint, char[] answer,
                char[] confirmation) { throw unsupported(); }
        @Override public boolean isEmailOtpConfigured() { return true; }
        @Override public EmailOtpChallenge beginRegistration(
                String name, String email, String studentIdentifier,
                char[] password, char[] passwordConfirmation,
                String question, String hint, char[] answer) {
            throw unsupported();
        }
        @Override public EmailOtpChallenge resendRegistration(String id) {
            throw unsupported();
        }
        @Override public AuthenticatedUser verifyRegistration(
                String id, String code) { throw unsupported(); }
        @Override public void cancelRegistration(String id) { }
        @Override public EmailOtpChallenge beginPasswordReset(String email) {
            throw unsupported();
        }
        @Override public EmailOtpChallenge resendPasswordReset(String id) {
            throw unsupported();
        }
        @Override public void completePasswordReset(
                String id, String code, char[] password,
                char[] confirmation) { throw unsupported(); }
        @Override public void cancelPasswordReset(String id) { }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("UI construction only");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
