package com.spendwise.auth;

public interface OwnerSetupService {

    boolean isOwnerSetupRequired();

    String getConfiguredOwnerEmail();

    UserSession createFirstOwner(
            String fullName,
            String email,
            char[] password,
            char[] passwordConfirmation);
}
