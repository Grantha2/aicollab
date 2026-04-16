# 2. Reading order

A 30-minute ordered walk. Read these files in this order and you will
understand how a debate actually runs end-to-end. Everything else in
the atlas builds on this core.

## Core path (30 minutes)

1. **`Main.java`** (CLI entry) —
   reads as a narrated wiring diagram. Steps 1–7 create Config,
   HTTP client, three `LlmClient`s, profile set, conversation context,
   and the `Maestro`. Reading this one file is the fastest way to see
   how everything is plugged together.

2. **`LlmClient.java`** — 112 lines. The interface every provider
   implements. Pay attention to the three entry points:
   `sendMessage(String)` (legacy), `sendMessage(LlmRequest)`
   (structured), `sendStateful(LlmRequest, previousStateId)`
   (server-side state chaining). Default implementations fall back
   gracefully so any provider can be upgraded independently.

3. **`LlmRequest.java`** — record type. `systemInstruction + messages
   + maxTokens + attachments`. The back-compat constructor at line
   32 is why adding the attachments field did not break a single
   existing caller.

4. **`Maestro.java`** (the 3-phase loop, ~378 lines) — skim the class
   header (lines 1–32), then read `runDebate()` at line 198. Phase 1
   (line 217), Phase 2 (line 253), Phase 3 (line 312). Note that
   Phase 1 requests include `attachments`; Phase 2/3 rely on
   provider-retained state to carry them forward.

5. **`PromptBuilder.java`** — the Context Layering Architecture
   (header comment at lines 17–29). System instruction is built once
   per Phase 1 turn from team context + agent identity + stakeholder
   + history; the user message carries only the question. This split
   is what lets Phase 2/3 chained turns re-use the server-side state
   without duplicating the system prompt.

6. **`AnthropicClient.java`** — pick one provider to study in depth;
   Anthropic is the most straightforward because it uses client-side
   state replay. You will see the `ConversationState` pattern
   (message list kept in memory, replayed on each chained turn).

7. **`SessionStore.java`** — 222 lines. Append-only JSONL: `appendTurn`,
   `appendSynthesis`, and the custom JSON extractors at lines 174–221.
   `Main.java:337-349` shows how resume works: read the file, replay
   turns and syntheses into `ConversationContext`.

## Optional deeper paths (add another 30 min each)

- **Agentic routines.** `AgenticTask.java` interface →
  `AgenticTaskRegistry.java` → `AgenticRoutinesPanel.java` (top 100
  lines only) → one concrete task like `UserWorkflowTask.java`.
- **Organizational context.** `OrganizationContext.java` →
  `ContextEntry.java` (freshness + status) → `ReconciliationService.java`
  (the SAFE_AUTO vs APPROVAL_REQUIRED gate) → `ContextChangeLog.java`
  (audit trail).
- **GUI shell.** `MainGui.java` top 200 lines for view switching and
  `onRunDebate()`; skip the helper methods on a first pass.

## What you do NOT need to read on the critical path

`ContextControlDialog`, `ButtonCreatorDialog`, `FirstLaunchSetupDialog`,
and most other `*Dialog.java` files are Swing construction code. They
are reference material, not conceptual material. Read them only if you
are working on that specific UI surface.
