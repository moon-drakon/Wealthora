package com.spendwise.service;

import java.util.Objects;

/** One record that could not be imported, with the reason it was left out. */
public record ImportIssue(String section, String reference, String reason) {

    public ImportIssue {
        section = Objects.requireNonNull(section, "Issue section is required.");
        reference = reference == null ? "" : reference;
        reason = Objects.requireNonNull(reason, "Issue reason is required.");
    }

    @Override
    public String toString() {
        return reference.isEmpty()
                ? section + ": " + reason
                : section + " " + reference + ": " + reason;
    }
}
