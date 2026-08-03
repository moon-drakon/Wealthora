package com.wealthora.server.api;

import jakarta.validation.constraints.NotBlank;

public record GoogleOAuthPollRequest(
        @NotBlank String flowIdentifier,
        @NotBlank String pollSecret) {

    @Override
    public String toString() {
        return "GoogleOAuthPollRequest[flowIdentifier=" + flowIdentifier
                + ", pollSecret=[REDACTED]]";
    }
}
