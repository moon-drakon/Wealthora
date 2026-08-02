package com.spendwise.auth;

public final class UnconfiguredGoogleAuthService implements GoogleAuthService {

    @Override
    public GoogleAuthorization authorize() {
        throw new AuthConfigurationException();
    }
}
