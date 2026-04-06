package collab;

// ============================================================
// Contribution.java — Tags a piece of user-provided context
// with WHO provided it, WHAT area it was about, and WHEN.
//
// WHAT THIS RECORD DOES (one sentence):
// Wraps every prompt/context a user submits with their identity
// and role so the centralized conversation history tracks
// attribution.
//
// HOW IT FITS THE ARCHITECTURE:
// When a user types a prompt and selects a functional area,
// Main.java creates a Contribution record. That record gets:
//   1. Stored in ConversationContext for in-session history
//   2. Persisted to session_data.json via SessionStore
//   3. Formatted via toTaggedString() for prompt injection
//
// WHY THIS MATTERS:
// The AI panel needs to know WHO said what. When the President
// contributes context about Finances and the Treasurer adds
// more later, the panel should see both contributions with
// clear attribution — not an anonymous blob of text.
// ============================================================

import java.time.Instant;

public record Contribution(
    String contributorName,
    String contributorRole,
    FunctionalArea area,
    String content,
    long epochMillis
) {

    // ============================================================
    // toTaggedString() — Formats this contribution for injection
    // into the conversation history.
    //
    // Example output:
    //   [Grant (President) | Finances | 2026-04-06T12:30:00Z]
    //   How should we handle the budget for next semester?
    // ============================================================
    public String toTaggedString() {
        return "[" + contributorName + " (" + contributorRole + ") | "
             + area.getDisplayName() + " | "
             + Instant.ofEpochMilli(epochMillis) + "]\n"
             + content;
    }
}
