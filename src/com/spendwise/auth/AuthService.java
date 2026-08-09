package com.spendwise.auth;

import java.util.List;

public interface AuthService {

    UserSession signInWithNsuEmail(String email, char[] password);

    default void changePassword(
            char[] currentPassword, char[] newPassword) {
        throw new AuthConfigurationException(
                "Password changes require a configured authentication service.");
    }

    default void setPassword(char[] newPassword) {
        throw new AuthConfigurationException(
                "Setting a password requires a configured authentication service.");
    }

    default List<AccountSession> listSessions() {
        throw new AuthConfigurationException(
                "Session management requires a configured authentication service.");
    }

    default void revokeSession(AccountSession session) {
        throw new AuthConfigurationException(
                "Session management requires a configured authentication service.");
    }

    default void logoutAll() {
        throw new AuthConfigurationException(
                "Session management requires a configured authentication service.");
    }

    void logout();

    AuthenticatedUser getCurrentUser();
}
