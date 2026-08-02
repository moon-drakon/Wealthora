package com.spendwise.auth;

import java.util.Objects;
import java.util.Optional;

public final class SessionManager {

    private UserSession currentSession;

    public synchronized Optional<UserSession> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    public synchronized void startSession(UserSession session) {
        UserSession required = Objects.requireNonNull(
                session, "User session is required.");
        currentSession = required;
    }

    public synchronized void clearSession() {
        currentSession = null;
    }
}
