package cowork.data;

// A next-best-action suggestion: what to do, why, how urgent (HIGH, MEDIUM or LOW), and the id
// of the agentic task that fulfils it.
public record Recommendation(
    String title,
    String reason,
    String urgency,
    String linkedTaskId
) {}
