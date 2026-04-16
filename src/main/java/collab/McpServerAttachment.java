package collab;

import java.util.List;

// ============================================================
// McpServerAttachment.java — Pointer to an external MCP server whose
// tools should be exposed to panelists during a debate.
//
// WHAT THIS RECORD DOES (one sentence):
// Describes how to reach one Model Context Protocol server (stdio
// command or HTTP URL) and which of its tools are allowed to be
// called, so McpHost can start a session against it and register
// proxied ToolSchemas with the ToolExecutor.
//
// HOW IT FITS THE ARCHITECTURE:
// McpServerAttachment is the third member of the ContextAttachment
// sealed hierarchy (after FileAttachment and the planned
// RagCorpusAttachment). When a debate starts, Maestro passes the
// attachment list to McpHost, which opens sessions for each MCP
// attachment, enumerates their tools via `tools/list`, filters by
// allowedTools, and registers each as a proxy handler on the
// ToolExecutor.
//
// WHY TWO TRANSPORTS:
//   stdio — a local subprocess that speaks JSON-RPC over its stdin/
//   stdout pipes. This is the canonical MCP transport for sandboxed
//   local tools (filesystem, retrieval, scratch workspace).
//   http  — a remote MCP server reached over HTTP with JSON-RPC.
//   Needed for shared-team deployments where a cloud-backed context
//   service exposes tools to every member of an organisation.
//
// WHY `allowedTools` IS A WHITELIST:
// MCP servers can expose arbitrary tools, including ones that were
// not yet written when the attachment was configured. A whitelist
// means no tool accidentally becomes callable after a server
// upgrade. An empty whitelist means "all tools the server advertises"
// — convenience for trusted, curated servers.
//
// SECURITY NOTE:
// This class only declares an attachment. The actual subprocess
// spawn / HTTP connection is McpHost's responsibility and gates on
// explicit user opt-in (no MCP server is loaded automatically from
// a profile set — the user confirms through the attachments dialog).
// ============================================================
public record McpServerAttachment(String name,
                                  String transport,
                                  String command,
                                  String url,
                                  List<String> allowedTools)
        implements ContextAttachment {

    public static final String TRANSPORT_STDIO = "stdio";
    public static final String TRANSPORT_HTTP = "http";

    public McpServerAttachment {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("McpServerAttachment requires a name");
        }
        if (transport == null
                || (!TRANSPORT_STDIO.equals(transport) && !TRANSPORT_HTTP.equals(transport))) {
            throw new IllegalArgumentException(
                    "transport must be \"stdio\" or \"http\", got: " + transport);
        }
        if (TRANSPORT_STDIO.equals(transport)
                && (command == null || command.isBlank())) {
            throw new IllegalArgumentException(
                    "stdio transport requires a non-blank command");
        }
        if (TRANSPORT_HTTP.equals(transport)
                && (url == null || url.isBlank())) {
            throw new IllegalArgumentException(
                    "http transport requires a non-blank url");
        }
        if (allowedTools == null) allowedTools = List.of();
        else allowedTools = List.copyOf(allowedTools);
    }
}
