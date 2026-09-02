package cowork.llm;

// ChatMessage — one role-tagged turn ("user" | "assistant") in a conversation,
// so the model can tell instructions from its own prior output.
public record ChatMessage(String role, String content) {}
