package collab;

// ============================================================
// ChatMessage.java — One turn in a model conversation.
//
// WHY THIS EXISTS:
// Multi-turn APIs need explicit role-tagged messages so models can
// distinguish user instructions from their own prior outputs.
// ============================================================
public record ChatMessage(String role, String content) {}
