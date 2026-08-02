package com.spendwise.auth;

import com.spendwise.auth.ui.AuthNavigator;
import com.spendwise.auth.ui.AuthenticatedProfilePanel;
import com.spendwise.auth.ui.ForgotPasswordPanel;
import com.spendwise.auth.ui.ResetPasswordPanel;
import com.spendwise.auth.ui.SignInPanel;
import com.spendwise.auth.ui.SignUpPanel;
import com.spendwise.auth.ui.VerificationPanel;
import com.spendwise.config.AppBrand;
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

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static int passed;

    private AuthClientFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        test("NSU email normalization", AuthClientFoundationTest::emailPolicy);
        test("password registration is NSU only",
                AuthClientFoundationTest::registrationPolicy);
        test("password sign-in rejects lookalike domains",
                AuthClientFoundationTest::signInPolicy);
        test("personal Gmail works through Google",
                AuthClientFoundationTest::googleGmail);
        test("Google identity requires verified email and subject",
                AuthClientFoundationTest::googleIdentityRules);
        test("matching NSU account linking model",
                AuthClientFoundationTest::linkingModel);
        test("profile badges follow provider and domain",
                AuthClientFoundationTest::profileBadges);
        test("unconfigured backend never authenticates",
                AuthClientFoundationTest::unconfiguredBackend);
        test("password copy clearing",
                AuthClientFoundationTest::passwordClearing);
        test("Google authorization code clearing",
                AuthClientFoundationTest::authorizationClearing);
        test("verified session lifecycle",
                AuthClientFoundationTest::sessionLifecycle);
        test("authentication panels express correct policy",
                AuthClientFoundationTest::panelsConstruct);
        System.out.println("All " + passed
                + " authentication policy tests passed.");
    }

    private static void emailPolicy() {
        assertEquals("student@northsouth.edu",
                NsuEmailPolicy.requireInstitutionalEmail(
                        " Student@NorthSouth.edu "));
        assertTrue(NsuEmailPolicy.isInstitutionalEmail(
                "student@northsouth.edu"));
        assertFalse(NsuEmailPolicy.isInstitutionalEmail("student@gmail.com"));
    }

    private static void registrationPolicy() {
        RecordingClient client = new RecordingClient();
        client.user = localUser(false, AccountStatus.PENDING_EMAIL_VERIFICATION);
        BackendAuthService service = new BackendAuthService(client);
        AuthenticatedUser result = service.registerWithNsuEmail(
                "Student", "student@northsouth.edu",
                "password1".toCharArray());
        assertFalse(result.isEmailVerified());
        expect(AuthException.class, () -> service.registerWithNsuEmail(
                "Student", "user@gmail.com", "password1".toCharArray()));
    }

    private static void signInPolicy() {
        BackendAuthService service = new BackendAuthService(
                new RecordingClient());
        expect(AuthException.class, () -> service.signInWithNsuEmail(
                "user@gmail.com", "password1".toCharArray()));
        expect(AuthException.class, () -> service.signInWithNsuEmail(
                "student@northsouth.edu.fake-domain.com",
                "password1".toCharArray()));
    }

    private static void googleGmail() {
        RecordingClient client = new RecordingClient();
        client.session = googleSession("person@gmail.com", "GOOGLE_SUBJECT_1");
        BackendAuthService service = new BackendAuthService(
                client, AuthClientFoundationTest::authorization);
        UserSession result = service.continueWithGoogle();
        assertEquals("person@gmail.com", result.getEmail());
        assertEquals("GOOGLE_SUBJECT_1", result.getGoogleSubjectId());
        assertEquals(AuthProvider.GOOGLE, result.getProvider());
    }

    private static void googleIdentityRules() {
        expect(AuthException.class, () -> new AuthenticatedUser(
                "USER_1", "Person", "person@gmail.com", true,
                AuthProvider.GOOGLE, "", AccountStatus.ACTIVE, NOW, NOW));
        expect(AuthException.class, () -> new AuthenticatedUser(
                "USER_1", "Person", "person@gmail.com", false,
                AuthProvider.GOOGLE, "GOOGLE_SUBJECT_1",
                AccountStatus.PENDING_EMAIL_VERIFICATION, NOW, NOW));
    }

    private static void linkingModel() {
        AuthenticatedUser linked = new AuthenticatedUser(
                "USER_1", "Student", "student@northsouth.edu", true,
                AuthProvider.LOCAL_AND_GOOGLE, "GOOGLE_SUBJECT_1",
                AccountStatus.ACTIVE, NOW, NOW);
        assertEquals(AuthProvider.LOCAL_AND_GOOGLE,
                linked.getPrimaryAuthProvider());
        assertEquals("GOOGLE_SUBJECT_1", linked.getGoogleSubjectId());
        expect(AuthException.class, () -> new AuthenticatedUser(
                "USER_2", "Person", "person@gmail.com", true,
                AuthProvider.LOCAL_AND_GOOGLE, "GOOGLE_SUBJECT_2",
                AccountStatus.ACTIVE, NOW, NOW));
    }

    private static void profileBadges() {
        assertEquals(List.of("Verified NSU Account"),
                localUser(true, AccountStatus.ACTIVE).getProfileBadges());
        assertEquals(List.of("Google Account"),
                googleSession("person@gmail.com", "SUBJECT_1")
                        .getUser().getProfileBadges());
        assertEquals(List.of("Google Account", "Verified NSU Email"),
                googleSession("student@northsouth.edu", "SUBJECT_2")
                        .getUser().getProfileBadges());
    }

    private static void unconfiguredBackend() {
        BackendAuthService service = new BackendAuthService(
                new UnconfiguredAuthApiClient());
        expect(AuthConfigurationException.class, () ->
                service.signInWithNsuEmail(
                        "student@northsouth.edu",
                        "password1".toCharArray()));
        expect(AuthConfigurationException.class, service::continueWithGoogle);
        expect(AuthConfigurationException.class, () ->
                service.registerWithNsuEmail(
                        "Student", "student@northsouth.edu",
                        "password1".toCharArray()));
        expect(AuthConfigurationException.class, () ->
                service.verifyNsuEmail("student@northsouth.edu", "123456"));
        expect(AuthConfigurationException.class, () ->
                service.resendVerification("student@northsouth.edu"));
        expect(AuthConfigurationException.class, () ->
                service.forgotPassword("student@northsouth.edu"));
        expect(AuthConfigurationException.class, () -> service.resetPassword(
                "student@northsouth.edu", "reset-token",
                "password2".toCharArray()));
    }

    private static void passwordClearing() {
        RecordingClient client = new RecordingClient();
        client.session = localSession();
        BackendAuthService service = new BackendAuthService(client);
        char[] original = "password1".toCharArray();
        service.signInWithNsuEmail("student@northsouth.edu", original);
        assertTrue(Arrays.equals("password1".toCharArray(), original));
        assertAllCleared(client.receivedPassword);
    }

    private static void authorizationClearing() {
        RecordingClient client = new RecordingClient();
        client.session = googleSession("person@gmail.com", "SUBJECT_1");
        BackendAuthService service = new BackendAuthService(
                client, AuthClientFoundationTest::authorization);
        service.continueWithGoogle();
        assertAllCleared(client.receivedAuthorizationCode);
    }

    private static void sessionLifecycle() {
        SessionManager sessions = new SessionManager();
        assertTrue(sessions.getCurrentSession().isEmpty());
        UserSession verified = googleSession("person@gmail.com", "SUBJECT_1");
        sessions.startSession(verified);
        assertEquals(verified, sessions.getCurrentSession().orElseThrow());
        sessions.clearSession();
        assertTrue(sessions.getCurrentSession().isEmpty());
        expect(AuthException.class, () -> new UserSession(
                localUser(false, AccountStatus.PENDING_EMAIL_VERIFICATION), NOW));
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
                        new SignUpPanel(service, sessions, navigator),
                        new VerificationPanel(service, navigator),
                        new ForgotPasswordPanel(service, navigator),
                        new ResetPasswordPanel(service, navigator),
                        new AuthenticatedProfilePanel(
                                service, sessions, navigator));
                for (JPanel panel : panels) {
                    assertTrue(panel.getComponentCount() > 0);
                }
                List<String> signInText = componentText(panels.get(0));
                assertContains(signInText, AppBrand.APP_NAME);
                assertContains(signInText, AppBrand.TAGLINE);
                assertContains(signInText, AppBrand.DESCRIPTION);
                assertContains(signInText, "Continue with Google");
                assertContainsPart(signInText,
                        "any verified Google account");
                assertContains(signInText, "NSU Email Access");
                assertContains(signInText, AppBrand.NSU_EMAIL_SUBTITLE);
                assertContains(signInText, "Remember Me");
                assertFalse(signInText.stream().anyMatch(value ->
                        value.contains("Google") && value.contains("NSU only")));
                assertContains(componentText(panels.get(1)),
                        "Continue with Google");
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError("Authentication panel test failed.",
                    failure.get());
        }
    }

    private static GoogleAuthorization authorization() {
        return new GoogleAuthorization(
                "one-time-code".toCharArray(), "http://127.0.0.1/callback");
    }

    private static AuthenticatedUser localUser(
            boolean verified, AccountStatus status) {
        return new AuthenticatedUser(
                "USER_LOCAL", "Student", "student@northsouth.edu",
                verified, AuthProvider.LOCAL, "", status, NOW, NOW);
    }

    private static UserSession localSession() {
        return new UserSession(localUser(true, AccountStatus.ACTIVE), NOW);
    }

    private static UserSession googleSession(String email, String subject) {
        AuthenticatedUser user = new AuthenticatedUser(
                "USER_GOOGLE", "Google User", email, true,
                AuthProvider.GOOGLE, subject, AccountStatus.ACTIVE, NOW, NOW);
        return new UserSession(user, NOW);
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

    private static void assertAllCleared(char[] value) {
        assertTrue(value != null);
        for (char character : value) {
            assertEquals('\0', character);
        }
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

    private static void assertContains(List<String> values, String expected) {
        assertTrue(values.contains(expected));
    }

    private static void assertContainsPart(
            List<String> values, String expected) {
        assertTrue(values.stream().anyMatch(value -> value.contains(expected)));
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true.");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">.");
        }
    }

    private static final class RecordingClient implements AuthApiClient {

        private UserSession session;
        private AuthenticatedUser user;
        private char[] receivedPassword;
        private char[] receivedAuthorizationCode;

        @Override
        public UserSession signInWithNsuEmail(String email, char[] password) {
            receivedPassword = password;
            return session;
        }

        @Override
        public UserSession continueWithGoogle(
                char[] authorizationCode, String redirectUri) {
            receivedAuthorizationCode = authorizationCode;
            return session;
        }

        @Override
        public AuthenticatedUser registerWithNsuEmail(
                String fullName, String email, char[] password) {
            receivedPassword = password;
            return user;
        }

        @Override
        public AuthenticatedUser verifyNsuEmail(
                String email, String verificationCode) {
            return user;
        }

        @Override
        public void resendVerification(String email) {
        }

        @Override
        public void forgotPassword(String email) {
        }

        @Override
        public void resetPassword(
                String email, String resetToken, char[] newPassword) {
            receivedPassword = newPassword;
        }

        @Override
        public UserSession refreshSession() {
            return session;
        }

        @Override
        public void logout() {
        }

        @Override
        public AuthenticatedUser getCurrentUser() {
            return user;
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

        @Override
        public void showAuthenticatedProfile(UserSession session) {
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
