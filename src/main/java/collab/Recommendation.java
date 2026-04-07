package collab;

// ============================================================
// Recommendation.java — A next-best-action recommendation.
//
// WHAT THIS CLASS DOES (one sentence):
// Immutable record representing a suggested action with urgency,
// reason, and a link to the agentic task that can fulfill it.
// ============================================================

public record Recommendation(
    String title,
    String reason,
    String urgency,    // HIGH, MEDIUM, LOW
    String linkedTaskId
) {
    public String urgencyEmoji() {
        return switch (urgency) {
            case "HIGH" -> "!!!";
            case "MEDIUM" -> "!!";
            default -> "!";
        };
    }
}
