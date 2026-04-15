package collab;

import java.util.List;

// ============================================================
// LlmRequest.java — Structured envelope for provider requests.
//
// WHY THIS EXISTS:
// During migration we need one request shape that can represent
// system instruction + ordered messages + token budget + any
// out-of-band context (files today; RAG corpora and MCP servers
// in later phases) regardless of provider-specific JSON dialect.
//
// ATTACHMENTS FIELD:
// `attachments` is ALWAYS non-null. Callers that don't need
// attachments use the three-arg constructor below, which supplies
// an empty list. Each LlmClient decides how to realise the list on
// the wire (Anthropic document blocks, OpenAI input_file blocks,
// Gemini fileData parts). The default sendMessage path ignores
// attachments, preserving the "flat prompt" behaviour mock clients
// already rely on.
// ============================================================
public record LlmRequest(String systemInstruction,
                         List<ChatMessage> messages,
                         int maxTokens,
                         List<ContextAttachment> attachments) {

    // Back-compat constructor so every existing call site that did
    // `new LlmRequest(sys, msgs, n)` keeps compiling. Supplies an
    // empty attachments list — the common case for anywhere that
    // isn't a user-driven debate turn.
    public LlmRequest(String systemInstruction,
                      List<ChatMessage> messages,
                      int maxTokens) {
        this(systemInstruction, messages, maxTokens, List.of());
    }

    // Canonical ctor normalises null attachments -> empty list so
    // downstream loops can unconditionally iterate.
    public LlmRequest {
        if (attachments == null) {
            attachments = List.of();
        }
    }
}
