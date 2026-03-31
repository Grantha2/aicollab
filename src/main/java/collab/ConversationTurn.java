package collab;

// ============================================================
// ConversationTurn.java — One recorded turn in debate history.
//
// WHY THIS EXISTS:
// ConversationContext currently stores only synthesis strings.
// This record is the foundation for structured memory so future
// prompts can reference specific phase/model turns.
// ============================================================
public record ConversationTurn(int cycle,
                               String phase,
                               String model,
                               String role,
                               String content,
                               long epochMillis) {}
