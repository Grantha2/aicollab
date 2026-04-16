# 8. Changelog

One-line entry per meaningful PR in reverse chronological order. Use
this to map "when did feature X land" back to a git history entry
without scrolling through every merge commit.

## 2026-04 — demo sprint

- **PR #28** *(this PR)* — Adds `docs/CODE_TOUR.md` + `docs/tour/*.md`
  covering the current codebase. Verifies that Gemini server-side
  state (from PR #27 base) is in place and ready for further
  tool-calling work. No code changes; documentation only.
- **PR #27** — Phase A of file attachments. Introduces
  `ContextAttachment` sealed interface, `FileAttachment`,
  `FileUploader` (per-provider multipart uploads + in-memory cache),
  `AttachmentsDialog`. Wires attachments into every provider client's
  Phase 1 request path.
- **Migrate Gemini to server-side state (4d42c4b).** Replaces the
  Gemini stateless-delegate `sendStateful` with a real implementation
  against the `/v1beta/interactions` beta endpoint. Introduces the
  `systemInstructionByStateId` cache because Google treats
  `system_instruction`, `generation_config`, and `tools` as
  interaction-scoped (not carried forward by `previous_interaction_id`).
  Falls back to stateless `sendMessage` on any non-200.
- **PR #26 / e8e86c3** — Profile editor NPE fix + edit menu + provider
  state-model documentation in the viewer.
- **bd18c01** — Context auditing (`ApiRequestLog` + viewer dialog),
  variable panelist counts (`PanelistSlot` list replaces the
  three-named-field constructor), profile editor UX fixes.
- **PR #25 / PR #24 / PR #23** — High-DPI rendering fixes, FlatLaf
  adoption, pixel-perfect fallback icons.

## Next planned landings (this sprint)

- **PR #29** — Tool-calling spine + MCP bridge. `ToolSchema`,
  `ToolCall`, `ToolResult`, `ToolExecutor` (5-iteration cap), `McpHost`
  with stdio + http transports, `McpServerAttachment` completing the
  sealed hierarchy. Each provider client gains a tool-use loop that
  conforms to its native protocol but is driven by the common
  `ToolSchema`.
- **PR #30** — Room-reservation workflow. `RoomReservationWorkflow`
  state machine, `RoomAvailabilityTool` (fixture + real UIC EMS via
  Claude native computer use with a local sandbox container),
  `PdfFillTool` (PDFBox against `assets/RSO-Facility-Request-Form.pdf`),
  `EmailDraftTool` (drafts only; user confirms send). Intent-screener
  dialog in front of the workflow.
- **PR #31** — Per-panelist `List<ContextAttachment>` +
  `List<ToolSchema>`, `AttachmentLibraryStore` for reusable files,
  `ApiRequestViewerDialog` gains Attachments and Tools columns with a
  tool-call trace drawer per row.
- **PR #32** — Conversation history sidebar (resumes any past session
  from the sessions directory), one-shot vs persistent-chat toggle,
  final polish pass on tour sections for anything landed that sprint.
