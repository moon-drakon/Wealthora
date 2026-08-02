package com.spendwise.auth;

import com.spendwise.auth.ui.AuthNavigator;
import com.spendwise.auth.ui.ForgotPasswordPanel;
import com.spendwise.auth.ui.ResetPasswordPanel;
import com.spendwise.auth.ui.SignInPanel;
import com.spendwise.auth.ui.SignUpPanel;
import com.spendwise.auth.ui.VerificationPanel;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class AuthClientFoundationTest {

    private static int passed;

    private AuthClientFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        test("NSU email normalization", AuthClientFoundationTest::emailPolicy);
        test("personal Gmail rejection", AuthClientFoundationTest::gmailRejected);
        test("unconfigured backend", AuthClientFoundationTest::unconfiguredBackend);
        test("verified session requirement", AuthClientFoundationTest::verifiedOnly);
        test("password copy clearing", AuthClientFoundationTest::passwordClearing);
        test("session lifecycle", AuthClientFoundationTest::sessionLifecycle);
        test("authentication panels", AuthClientFoundationTest::panelsConstruct);
        System.out.println("All " + passed
                + " authentication foundation tests passed.");
    }

    private static void emailPolicy() {
        assertEquals("student@northsouth.edu",
                NsuEmailPolicy.requireInstitutionalEmail(
                        " Student@NorthSouth.edu "));
    }

    private static void gmailRejected() {
        expect(AuthException.class, () ->
                NsuEmailPolicy.requireInstitutionalEmail("student@gmail.com"));
        expect(AuthException.class, () ->
                NsuEmailPolicy.requireInstitutionalEmail(
                        "student@northsouth.edu.example.com"));
    }

    private static void unconfiguredBackend() {
        BackendAuthService service = new BackendAuthService(
                new UnconfiguredAuthApiClient());
        expect(AuthConfigurationException.class, () -> service.signIn(
                "student@northsouth.edu", "password1".toCharArray()));
        expect(AuthConfigurationException.class, service::continueWithGoogle);
        expect(AuthConfigurationException.class, () -> service.createAccount(
                "Student", "student@northsouth.edu",
                "password1".toCharArray()));
        expect(AuthConfigurationException.class, () -> service.verifyEmail(
                "student@northsouth.edu", "123456"));
        expect(AuthConfigurationException.class, () ->
                service.requestPasswordReset("student@northsouth.edu"));
        expect(AuthConfigurationException.class, () -> service.resetPassword(
                "reset-token", "password2".toCharArray()));
    }

    private static void verifiedOnly() {
        RecordingClient client = new RecordingClient(false);
        BackendAuthService service = new BackendAuthService(client);
        expect(AuthException.class, () -> service.signIn(
                "student@northsouth.edu", "password1".toCharArray()));
        expect(AuthException.class, service::continueWithGoogle);
    }

    private static void passwordClearing() {
        RecordingClient client = new RecordingClient(true);
        BackendAuthService service = new BackendAuthService(client);
        char[] original = "password1".toCharArray();
        UserSession session = service.signIn(
                "student@northsouth.edu", original);
        assertTrue(session.isVerified());
        assertTrue(Arrays.equals("password1".toCharArray(), original));
        assertTrue(client.receivedPassword != null);
        for (char value : client.receivedPassword) {
            assertEquals('\0', value);
        }
    }

    private static void sessionLifecycle() {
        SessionManager sessions = new SessionManager();
        assertTrue(sessions.getCurrentSession().isEmpty());
        UserSession verified = session(true);
        sessions.startSession(verified);
        assertEquals(verified, sessions.getCurrentSession().orElseThrow());
        sessions.clearSession();
        assertTrue(sessions.getCurrentSession().isEmpty());
        expect(AuthException.class, () ->
                sessions.startSession(session(false)));
    }

    private static void panelsConstruct() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                AuthService service = new BackendAuthService(
                        new UnconfiguredAuthApiClient());
                SessionManager sessions = new SessionManager();
                AuthNavigator navigator = new NoOpNavigator();
                List<JPanel> panels = List.of(
                        new SignInPanel(service, sessions, navigator),
                        new SignUpPanel(service, navigator),
                        new VerificationPanel(service, sessions, navigator),
                        new ForgotPasswordPanel(service, navigator),
                        new ResetPasswordPanel(service, navigator));
                for (JPanel panel : panels) {
                    assertTrue(panel.getComponentCount() > 0);
                }
                List<String> signInText = componentText(panels.get(0));
                assertTrue(signInText.contains("Continue with Google"));
                assertTrue(signInText.stream().anyMatch(value ->
                        value.contains("@northsouth.edu")));
                assertTrue(signInText.stream().anyMatch(value ->
                        value.contains("Personal Gmail")));
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError("Authentication panel test failed.",
                    failure.get());
        }
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

    private static UserSession session(boolean verified) {
        return new UserSession(
                "USER_1",
                "student@northsouth.edu",
                "Student",
                verified,
                UserSession.Provider.NSU_PASSWORD,
                Instant.now());
    }

    private static void test(String name, ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            passed++;
        } catch (Throwable failure) {
            throw new AssertionError(name + " failed", failure);
        }
    }

    private static void expect(
            Class<? extends Throwable> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError(
                    "Expected " + type.getSimpleName() + " but caught "
                    + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + ".");
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    private static final class RecordingClient implements AuthApiClient {

        private final boolean verified;
        private char[] receivedPassword;

        private RecordingClient(boolean verified) {
            this.verified = verified;
        }

        @Override
        public UserSession signIn(String email, char[] password) {
            receivedPassword = password;
            return session(verified);
        }

        @Override
        public UserSession signInWithGoogle() {
            return new UserSession("USER_1", "student@northsouth.edu",
                    "Student", verified, UserSession.Provider.GOOGLE,
                    Instant.now());
        }

        @Override
        public UserSession createAccount(
                String displayName, String email, char[] password) {
            receivedPassword = password;
            return session(false);
        }

        @Override
        public UserSession verifyEmail(String email, String verificationCode) {
            return session(true);
        }

        @Override
        public void requestPasswordReset(String email) {
        }

        @Override
        public void resetPassword(String resetToken, char[] newPassword) {
            receivedPassword = newPassword;
        }
    }

    private static final class NoOpNavigator implements AuthNavigator {

        @Override
        public void showSignIn() {
        }

        @Override
        public void showSignUp() {
        }

        @Override
        public void showVerification(String email) {
        }

        @Override
        public void showForgotPassword() {
        }

        @Override
        public void showResetPassword() {
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
