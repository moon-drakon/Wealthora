package com.spendwise.auth;

/** Public recovery information. The protected answer is never exposed. */
public record PasswordRecoveryChallenge(String question, String hint) {

    public PasswordRecoveryChallenge {
        question = required(question, "Recovery question");
        hint = hint == null ? "" : hint.strip();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AuthException(name + " is required.");
        }
        return value.strip();
    }
}
