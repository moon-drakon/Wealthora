package com.spendwise.auth;

import java.util.Objects;

public final class AuthorizationService {

    public void requireAdmin(UserSession session) {
        UserSession required = requireSession(session);
        if (!required.canAccessAdminConsole()) {
            throw new AuthException("Administrator permission is required.");
        }
    }

    public void requireOwner(UserSession session) {
        UserSession required = requireSession(session);
        if (!required.isOwner()) {
            throw new AuthException("Owner permission is required.");
        }
    }

    public void requireCanManageAdministrators(
            UserSession actor, AuthenticatedUser target) {
        requireOwner(actor);
        AuthenticatedUser requiredTarget = Objects.requireNonNull(
                target, "Target user is required.");
        if (requiredTarget.hasRole(UserRole.OWNER)) {
            throw new AuthException("The primary OWNER cannot be modified.");
        }
    }

    public void requireOwnWorkspace(
            UserSession session, String requestedUserIdentifier) {
        UserSession required = requireSession(session);
        if (!required.getUserIdentifier().equals(requestedUserIdentifier)) {
            throw new AuthException("Cross-user finance access is denied.");
        }
    }

    private static UserSession requireSession(UserSession session) {
        return Objects.requireNonNull(session, "An authenticated session is required.");
    }
}
