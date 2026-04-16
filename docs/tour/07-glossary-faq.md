# 7. Glossary and professor FAQ

## Glossary

**Agent / panelist.** One seat in the debate. Has an `AgentProfile`
(name, perspective, lens) assigned to it. Two panelists can share the
same provider and model but have different perspectives.

**Slot.** The seat itself, represented by `PanelistSlot`. A slot
bundles `provider + model + agent profile`.

**Stakeholder.** The human user whose question drives the debate. The
"hotseat." A stakeholder has a role, focus area, and KPIs that get
injected into every prompt.

**Phase 1 / 2 / 3.**
- Phase 1 is independent responses (each panelist answers the question
  without seeing others).
- Phase 2 is cross-reaction (each panelist sees every other panelist's
  latest response and reacts). May repeat over `debateRounds`.
- Phase 3 is synthesis (slot 0 aggregates everything into a report).

**Cycle.** One full Phase 1 → Phase 2 → Phase 3 execution. A debate
session can contain many cycles; each cycle's synthesis becomes history
for the next.

**Round.** A single pass of Phase 2 across all panelists. Multiple
rounds let panelists react to each other's reactions, not just to
Phase 1 responses.

**StateId.** A provider-specific handle that chains one turn to the
next. OpenAI calls it `previous_response_id`; Gemini calls it
`previous_interaction_id`; Anthropic has no server-side equivalent and
so our client replays the full message history client-side.

**Context Layering Architecture (CLA).** The layered system prompt:
team context + agent identity + stakeholder briefing + conversation
history. Built once per Phase 1 turn by `PromptBuilder.buildSystemInstruction`.

**Organizational context.** The ten-field `OrganizationContext`
record: priorities, initiatives, deadlines, metrics, blockers,
decisions, preferred tone, and so on. Every agentic task reads from
it; the reconciliation pipeline gates writes.

**Reconciliation.** The gate between AI-proposed context changes and
the persisted org state. `SAFE_AUTO` changes apply immediately;
`APPROVAL_REQUIRED` changes queue for human sign-off
(`ReconciliationService.java`).

**Freshness.** Each `ContextEntry` knows when it was last updated and
which TTL applies. The enum is `FRESH | AGING | STALE |
NEEDS_CONFIRMATION`.

**Attachment.** A `ContextAttachment` (today always `FileAttachment`)
carried on an `LlmRequest`. Each client encodes it per provider; the
orchestrator stays provider-agnostic.

**Agentic routine / task.** Anything implementing `AgenticTask`. Lives
in the third view of the GUI. Can read org context, make LLM calls,
propose context updates.

---

## FAQ — questions a professor is likely to ask

**Q: Why three separate client classes? Why not one generic HTTP client?**

Because the three providers' wire formats differ beyond a parameter
swap. Claude uses an `anthropic-version` header plus `messages: [{role,
content}]`. OpenAI's Responses API uses `Authorization: Bearer` plus a
flat `input` array with `previous_response_id`. Gemini uses
`x-goog-api-key` plus `contents: [{role, parts: [{text}]}]` on
generateContent and a different body shape entirely on Interactions.
The `LlmClient` interface is the abstraction — the three classes are
the per-provider adapters. Standard strategy pattern.

**Q: Why does Gemini need to re-send `system_instruction` every turn
when the other providers do not?**

Because Google's Interactions API treats `system_instruction`,
`generation_config`, and `tools` as **interaction-scoped**:
`previous_interaction_id` carries only inputs and outputs forward.
Anthropic's client-side replay naturally carries everything. OpenAI's
Responses API retains the instructions server-side. Only Gemini forces
us to keep a local cache of system instructions and re-attach them on
each chained call (`GeminiClient.java:79, 298-304, 378-381`).

**Q: Why JSONL for sessions but JSON for profiles?**

Append-only writes. A debate writes one turn per API call. If the
process crashes mid-write, the worst case is a truncated final line —
never a corrupted JSON document. Profiles are written as whole files
from memory, so a complete JSON object is fine there.

**Q: Why is `ContextAttachment` sealed?**

Because we want the compiler to force every provider's switch
statement to handle every attachment type we support. If `FileAttachment`
is ever joined by `RagCorpusAttachment` or `McpServerAttachment`, the
unresolved `switch` branches become compile errors — we cannot
accidentally ship a client that silently ignores a new attachment type.

**Q: Why does `Maestro` accept a `List<LlmClient>` instead of three
named fields (claude, gpt, gemini)?**

Because two Claudes + one GPT is a legal panel configuration. Hardcoding
the vendors into field names would have made "variable panelists" a
breaking change. The list-based constructor makes panel size and vendor
mix a data concern, not a code concern (`Maestro.java:103-126`).

**Q: Why is `ConversationContext` character-bounded instead of
token-bounded?**

Because counting tokens accurately requires invoking the provider's
tokenizer, which differs per provider and would couple this class to
all three. Characters are a rough proxy (roughly 4 per token for
English). The budget is conservative and overflow protection trims
oldest entries first (`ConversationContext.java:149-157`).

**Q: Why does `FileUploader` not persist provider file IDs?**

Because the IDs expire. Anthropic file IDs persist for ~30 days, Gemini
for ~48 hours, OpenAI until revoked. Persisting an expired ID and
replaying it after a restart would silently drop the attachment with a
cryptic error. The cache is process-lifetime on purpose
(`FileUploader.java:36-53`).

**Q: Where is the "intent capture" for agentic tasks?**

Today, in the dialogs owned by each `AgenticTask` implementation —
each task asks its own follow-up questions before producing output
(see `TaskQuestionDialog`, `UserWorkflowTask`). PR #30 generalises
this with an "intent screener" shown before the room-reservation
workflow kicks off.
