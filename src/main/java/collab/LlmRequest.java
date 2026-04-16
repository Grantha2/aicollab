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
                         List<ContextAttachment> attachments,
                         List<ToolSchema> tools) {

    // Back-compat constructor (pre-attachments era). Existing callers
    // that did `new LlmRequest(sys, msgs, n)` still compile and still
    // get an empty attachments list and an empty tools list.
    public LlmRequest(String systemInstruction,
                      List<ChatMessage> messages,
                      int maxTokens) {
        this(systemInstruction, messages, maxTokens, List.of(), List.of());
    }

    // Back-compat constructor for the Phase-A attachments era. Callers
    // that already wire in attachments but not tools keep working;
    // they receive an empty tools list by default.
    public LlmRequest(String systemInstruction,
                      List<ChatMessage> messages,
                      int maxTokens,
                      List<ContextAttachment> attachments) {
        this(systemInstruction, messages, maxTokens, attachments, List.of());
    }

    // Canonical ctor normalises null list fields -> empty list so
    // downstream loops can unconditionally iterate. Gson and test
    // code that round-trips this record via reflection both benefit.
    public LlmRequest {
        if (attachments == null) attachments = List.of();
        if (tools == null) tools = List.of();
    }
}
