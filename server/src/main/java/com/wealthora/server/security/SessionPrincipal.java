package com.wealthora.server.security;

import java.util.Set;
import java.util.UUID;

public record SessionPrincipal(
        UUID sessionId, UUID userId, Set<String> roles) {
}
