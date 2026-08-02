package com.spendwise.voice;

import java.util.List;
import java.util.Objects;

public record VoiceParseResult(
        String transcript,
        VoiceTransactionDraft draft,
        List<String> warnings) {

    public VoiceParseResult {
        transcript = Objects.requireNonNull(
                transcript, "Transcript is required.").strip();
        Objects.requireNonNull(draft, "Voice transaction draft is required.");
        warnings = List.copyOf(Objects.requireNonNull(
                warnings, "Voice parse warnings are required."));
    }

    public List<String> allReviewMessages() {
        java.util.ArrayList<String> messages = new java.util.ArrayList<>(warnings);
        messages.addAll(draft.findValidationProblems());
        return List.copyOf(messages);
    }
}
