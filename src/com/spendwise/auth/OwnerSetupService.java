package com.spendwise.auth;

public interface OwnerSetupService {

    boolean isOwnerSetupRequired();

    String getConfiguredOwnerEmail();

    UserSession createFirstOwner(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation);

    default UserSession createFirstOwner(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation,
            String recoveryQuestion,
            String recoveryHint,
            char[] recoveryAnswer) {
        return createFirstOwner(
                fullName, email, password, passwordConfirmation);
    }
}
