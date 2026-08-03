package com.wealthora.server.api;

import java.time.Instant;

public record ApiError(String code, String message, Instant occurredAt) {
}
