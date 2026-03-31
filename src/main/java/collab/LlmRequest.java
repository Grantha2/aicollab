package collab;

import java.util.List;

// ============================================================
// LlmRequest.java — Structured envelope for provider requests.
//
// WHY THIS EXISTS:
// During migration we need one request shape that can represent
// system instruction + ordered messages + token budget regardless
// of provider-specific JSON dialect.
// ============================================================
public record LlmRequest(String systemInstruction,
                         List<ChatMessage> messages,
                         int maxTokens) {}
