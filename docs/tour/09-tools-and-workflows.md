# 9. Tool-calling, MCP, and agentic workflows

This section covers the experimental-branch additions: the tool-calling
spine, the MCP client, the room-reservation workflow, and the new
library / sidebar / viewer surfaces. These sit on top of the core
debate engine documented in sections 1–8.

## The tool-calling spine

Three small records and one executor:

- `ToolSchema` — name + description + JSON-schema parameter tree.
  Two factory helpers (`noArgs`, `stringParams`) cover the common
  cases; the full constructor accepts any nested Map for richer
  parameter shapes.
- `ToolCall` — id + name + argument map. Populated by each provider
  client when it parses a tool-use response.
- `ToolResult` — callId + content string + isError flag. Factories:
  `ok(id, content)` and `error(id, message)`.
- `ToolExecutor` — name-keyed registry. `executeAll(calls)` runs a
  batch synchronously, catches exceptions, and returns error results
  instead of letting them propagate. Iteration cap lives on
  `ToolExecutor.DEFAULT_MAX_ITERATIONS` (5).

Each provider client serialises the same `ToolSchema` into its own
wire format. Today only `AnthropicClient` implements the full loop
(`AnthropicClient.java:~500+`); `OpenAiClient` and `GeminiClient`
inherit the no-op default from `LlmClient` so the debate keeps
running for providers that have not yet been extended.

## The tool-use loop, traced

1. Maestro (or a workflow task) constructs an `LlmRequest` with
   `tools` populated from `executor.schemas()`.
2. The client serialises tools into the provider-native shape
   (Anthropic: `tools: [{name, description, input_schema}]`) and
   sends the turn.
3. Response inspection:
   - Anthropic: if `stop_reason == "tool_use"`, the content array
     contains `{type:"tool_use", id, name, input}` blocks. The client
     collects them into `ToolCall` instances.
4. `executor.executeAll(calls)` runs each handler; each call's
   result is wrapped into `ToolResult`.
5. The client appends a user message containing matching
   `tool_result` blocks (with `tool_use_id` echoed) and re-sends.
6. Loop until `stop_reason != "tool_use"` or the cap is hit. Final
   text is returned via `StatefulResponse.text()`.

## MCP (Model Context Protocol)

Two classes:

- `McpClientSession` — one JSON-RPC 2.0 session against one MCP
  server. Supports stdio (spawn subprocess, newline-delimited JSON
  frames) and http (POST each frame). Implements the minimum surface:
  `initialize`, `tools/list`, `tools/call`.
- `McpHost` — lifecycle owner. Start-of-debate: walk the attachment
  list, open a session per `McpServerAttachment`, enumerate tools,
  filter by the attachment's whitelist, register each as a namespaced
  (`server.tool`) proxy handler on the shared `ToolExecutor`. Failures
  are logged and skipped — the debate continues with whichever
  servers started successfully.

`McpServerAttachment` is the third member of the `ContextAttachment`
sealed hierarchy alongside `FileAttachment`. Every provider client's
attachment-handling switch therefore acquires a branch for it when
tool-support lands for that provider.

## The room-reservation workflow

Lives in `collab.workflows`, four tool classes plus one orchestrator:

- `RoomAvailabilityTool` — two modes. In `fixture` mode it reads
  `assets/fixtures/room_availability.html`; in `live` mode it tells
  Claude to drive UIC's ASP.NET EMS page via the computer-use tools.
- `PdfFillTool` — PDFBox AcroForm fill against
  `assets/RSO-Facility-Request-Form.pdf`. Writes to
  `~/aicollab-filled/`. Gracefully errors on flat-scan PDFs.
- `EmailDraftTool` — returns a plain-text email draft. Never sends
  — that is a separate user-gated action.
- `ComputerUseToolProxy` — exposes Claude's native
  `computer_20241022`, `bash_20241022`, `text_editor_20241022` tool
  types as HTTP proxies to a local sandbox container. See
  `docs/SANDBOX.md` for the runbook. Unreachable sandbox returns
  error results; Claude can recover textually.
- `RoomReservationWorkflow` — implements `AgenticTask`. Collects
  intent via a small Swing dialog, registers all tools on a fresh
  `ToolExecutor`, and fires one Claude turn with tools available.
  SwingWorker keeps the EDT responsive during the tool-use loop.
  Registered in `MainGui.java` alongside the other built-in tasks so
  it appears in the Agentic Routines sidebar automatically.

## Per-panelist attachments and tools

`PanelistSlot` gains two additive fields: `attachments`
(`List<FileAttachment>`) and `allowedTools` (`List<String>`).
`Maestro.runDebate` unions debate-level attachments with per-slot
attachments on each Phase 1 turn, so slot-specific files travel only
with the intended panelist. `allowedTools` is persisted today but not
yet consulted by the clients' tool loops — the enforcement step is
the obvious next slice. Legacy profile-set JSON files remain valid;
both fields default to empty.

## Attachment library and conversation history sidebar

- `AttachmentLibraryStore` (new) — `library/attachments.json`, same
  Gson pattern as `ProfileLibrary`. Holds reusable files (the RSO
  form, standing budget templates) that users promote into a
  profile set or a slot as needed. Provider IDs stay in
  `FileUploader`'s in-memory cache; the library never persists them.
- `ConversationHistorySidebar` (new) — a `JDialog` listing every
  JSONL session file under `sessions/`, newest first, with a preview
  pane showing the latest synthesis (or the last turn for empty
  sessions). "Resume" replays the file's turns and syntheses into
  the active `ConversationContext` and rebuilds the Maestro against
  that `SessionStore` so new turns append to the same file. Wired
  into the Context menu as `Ctrl+Shift+H`.

## API Request Viewer enrichments

`ApiRequestLog.RequestRecord` gains `attachmentsSummary` and
`toolsSummary` string fields populated in `from()`. The viewer's
Meta tab now shows both lines so reviewers can see what travelled
with each request — which files, which tools — without opening the
full JSONL body. The table-style columns are the next UI upgrade;
today the summaries live on the detail pane.

## What this section does NOT cover (yet)

- Full OpenAI / Gemini tool loops. Both still inherit the default
  no-op behaviour; writing the loops is tractable but was
  deliberately deferred to keep the experimental branch focused on
  the Claude-only demo path (computer-use is Claude-native).
- A dedicated Tools tab in the viewer with an expandable trace
  drawer of tool_use/tool_result pairs per turn. The Meta-line
  summaries suffice for audit; a richer view is the follow-up.
- One-shot vs persistent chat toggle. The mechanic is simple
  (`SessionStore.createNewDefaultSession()` every turn vs every
  debate) but the accompanying intent-screener UX work is its own
  slice.
