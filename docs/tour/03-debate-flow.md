# 3. Debate call flow

One user prompt, traced end to end. Memorise this trace — it is the
single most valuable thing for an oral code-review.

## Setup (before any API call)

1. User types a prompt and clicks **Run Debate** in the GUI, or types
   it after `SEND` in the CLI.
2. `MainGui.onRunDebate()` (or `Main.java:272`) resolves the active
   stakeholder and hands both to `Maestro.runDebate(prompt, stakeholder)`
   — see `Maestro.java:198`.
3. `Maestro` allocates three per-slot arrays: `stateIds`, `phase1`,
   `latest` (`Maestro.java:205-207`). These track chained state and
   carry responses between phases.

## Phase 1 — independent responses

For each panelist slot (`Maestro.java:217-242`):

1. Build an `LlmRequest` with
   - **system instruction** = team context + agent identity + stakeholder
     + history, from `PromptBuilder.buildSystemInstruction()`
     (`PromptBuilder.java:132-138`),
   - **messages** = one user message with the question body,
   - **attachments** = the debate-level `List<ContextAttachment>` set
     via `Maestro.setAttachments()` (`Maestro.java:150-156`).
2. Log the request to `ApiRequestLog` (audit trail).
3. Call `clients.get(i).sendStateful(req, null)`. The null `previousStateId`
   tells the provider "this is a fresh chain, store state if you can."
4. Capture returned text + `stateId` into the per-slot arrays.
5. Persist the turn (`Maestro.java:238`, `SessionStore.appendTurn`).
6. Fire `DebateListener.onPhase1Response` so the GUI streams panel
   updates live.

## Phase 2 — cross-reaction (repeats `debateRounds` times)

For each round and each slot (`Maestro.java:253-310`):

1. Build a peer list of every OTHER slot's `latest[j]` response.
2. Build a user message via `PromptBuilder.buildPhase2PeerMessage` —
   this is ONLY the reaction body. System instruction is intentionally
   null (`Maestro.java:288`) because the provider (or our replay shim
   for Anthropic) already holds it.
3. Call `clients.get(i).sendStateful(req, stateIds[i])`. The
   `previousStateId` chains this turn to the panelist's own Phase 1
   history.
4. Update `stateIds[i]` with the returned id, write the reaction to
   `roundOut[i]`.
5. After all slots finish this round, `latest = roundOut` (snapshot
   semantics — no panelist sees this round's reactions while the round
   is in flight).

## Phase 3 — synthesis

1. Slot 0's client produces the synthesis (`Maestro.java:340-348`). Its
   server-side state already contains Phase 1 + all Phase 2 rounds, so
   the synthesis prompt is again just the reaction body.
2. `PromptBuilder.buildPhase3SynthesisMessage` composes a structured
   prompt that lists Phase 1 responses + final reactions per panelist
   and asks for the structured synthesis report.
3. Synthesis text is appended to `ConversationContext` and persisted
   to `SessionStore`.
4. `DebateListener.onSynthesis` fires → GUI displays the synthesis tab.

## Why this structure matters

- **No panelist count is baked in.** The same loop handles 2, 3, or 8
  slots (`Maestro.java:115-117` enforces ≥2). Two Claudes + one GPT is
  a valid debate.
- **State is per-slot.** If a slot's API call fails, other slots keep
  working — the failing slot just returns an error string and the
  debate continues.
- **API call budget is predictable.** `1 + N + (N × debateRounds)`
  calls per debate (`Maestro.java:374-377`). For a 3-panelist,
  1-round debate: 1 + 3 + 3 = 7 calls. Easy to reason about cost.

## Where tool calls will hook in (PR #29)

Every `sendStateful` call becomes a tool-call *loop*: the provider may
return a tool_use / function_call block instead of final text, in which
case the client invokes the tool, appends the result to the conversation,
and asks the provider to continue. The loop is capped at 5 iterations.
No change to `Maestro` — the loop lives inside each client.
