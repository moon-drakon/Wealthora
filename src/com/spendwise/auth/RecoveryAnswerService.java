package com.spendwise.auth;

import java.util.Arrays;
import java.util.Locale;

/** Normalizes and protects recovery answers using the password hash service. */
public final class RecoveryAnswerService {

    private static final String HASH_PREFIX = "Recovery1:";
    private static final int MINIMUM_LENGTH = 3;
    private static final int MAXIMUM_LENGTH = 80;
    private final PasswordService passwordService;

    public RecoveryAnswerService(PasswordService passwordService) {
        this.passwordService = java.util.Objects.requireNonNull(passwordService);
    }

    public String hash(char[] answer) {
        char[] protectedAnswer = protectedAnswer(answer);
        try {
            return passwordService.hash(protectedAnswer);
        } finally {
            Arrays.fill(protectedAnswer, '\0');
        }
    }

    public boolean matches(char[] answer, String hash) {
        try {
            char[] protectedAnswer = protectedAnswer(answer);
            try {
                return passwordService.matches(protectedAnswer, hash);
            } finally {
                Arrays.fill(protectedAnswer, '\0');
            }
        } catch (AuthException exception) {
            return false;
        }
    }

    public String normalizeForComparison(char[] answer) {
        return normalized(answer);
    }

    private static char[] protectedAnswer(char[] answer) {
        return (HASH_PREFIX + normalized(answer)).toCharArray();
    }

    private static String normalized(char[] answer) {
        if (answer == null) {
            throw new AuthException("Recovery answer is required.");
        }
        String normalized = new String(answer).strip()
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.length() < MINIMUM_LENGTH) {
            throw new AuthException(
                    "Recovery answer must contain at least 3 characters.");
        }
        if (normalized.length() > MAXIMUM_LENGTH) {
            throw new AuthException(
                    "Recovery answer must not exceed 80 characters.");
        }
        return normalized;
    }
}
