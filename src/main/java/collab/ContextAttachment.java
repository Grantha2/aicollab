package collab;

// ============================================================
// ContextAttachment.java — Pluggable "extra context" for a request.
//
// WHAT THIS SEALED INTERFACE DOES (one sentence):
// Lets callers attach something beyond plain chat messages — a file
// today, a RAG corpus or an MCP tool-server in later phases — to an
// LlmRequest without changing its shape every time a new attachment
// type is introduced.
//
// HOW IT FITS THE ARCHITECTURE:
// LlmRequest carries a List<ContextAttachment>. Each LlmClient decides
// how to realise an attachment on the wire (Anthropic document blocks,
// OpenAI input_file blocks, Gemini fileData parts). Maestro doesn't
// know or care about the provider-specific encoding — it just forwards
// the attachment list.
//
// WHY SEALED:
// Only this package should add new attachment types. Making the set
// closed means the exhaustive switch in each client — "case FileAttachment
// f -> …" — is the compiler-enforced source of truth for "every client
// handles every attachment type we support today".
//
// PHASE A — ONLY FileAttachment IS IMPLEMENTED.
// The permits list already reserves slots for the two follow-on phases
// (RagCorpusAttachment, McpServerAttachment) so the wire-up pattern is
// in place when we get there, but neither exists yet.
// ============================================================
public sealed interface ContextAttachment permits FileAttachment {
}
