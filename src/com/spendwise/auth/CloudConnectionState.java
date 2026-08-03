package com.spendwise.auth;

public enum CloudConnectionState {
    CONNECTED("Connected"),
    OFFLINE("Offline"),
    UNAUTHORIZED("Unauthorized"),
    SERVER_UNAVAILABLE("Server unavailable");

    private final String displayName;

    CloudConnectionState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
