package com.spendwise.auth;

public final class AuthConfigurationException extends AuthException {

    public AuthConfigurationException() {
        super("Authentication requires backend configuration.");
    }

    public AuthConfigurationException(String message) {
        super(message);
    }

    public AuthConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
