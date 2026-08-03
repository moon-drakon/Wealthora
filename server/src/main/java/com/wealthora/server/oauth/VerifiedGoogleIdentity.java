package com.wealthora.server.oauth;

import java.time.Instant;
import java.util.List;

public record VerifiedGoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String hostedDomain,
        String fullName,
        String nonce,
        String issuer,
        List<String> audience,
        Instant expiresAt) {
}
