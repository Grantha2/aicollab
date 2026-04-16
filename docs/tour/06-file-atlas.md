# 6. File atlas

Tiered reference. Read Tier 1 in depth before rehearsing any oral
code-review; Tier 2 is "know what it does and where it lives"; Tier 3
is look-it-up material.

Every file listed here already carries a pedagogical header comment
(`// ============ WHAT THIS CLASS DOES ==========`). The atlas points
you to the file; the file explains itself.

## Tier 1 — must be able to explain

| File | Role |
|------|------|
| `Main.java` | CLI entry; narrated wiring for every major subsystem. |
| `MainGui.java` | GUI shell; `CardLayout` switching; `onRunDebate()` delegates to Maestro. |
| `Maestro.java` | The 3-phase loop. `runDebate` is the keystone method. |
| `LlmClient.java` | Interface all three providers implement. |
| `LlmRequest.java` | Record; `systemInstruction + messages + maxTokens + attachments`. |
| `StatefulResponse.java` | Record; `text + stateId` returned by `sendStateful`. |
| `ChatMessage.java` | Record; `role + content`. |
| `AnthropicClient.java` | Client-side state replay pattern. |
| `OpenAiClient.java` | Responses API; chains by `previous_response_id`. |
| `GeminiClient.java` | Interactions API; chains by `previous_interaction_id`; re-sends `system_instruction` each turn (cached in `systemInstructionByStateId`). |
| `PromptBuilder.java` | Context Layering Architecture (team + agent + stakeholder + history). |
| `ConversationContext.java` | In-memory history; trims oldest first when over budget. |
| `SessionStore.java` | Append-only JSONL; resume replays the file. |
| `PanelistSlot.java` | One seat = provider + model + agent profile. |
| `ProfileSet.java` | Named bundle of slots + stakeholders + team context + attachments. |

## Tier 2 — know-by-purpose

### Config & domain values
- `Config.java` — API keys + tunables from `config.properties`.
- `Provider.java` — enum `ANTHROPIC | OPENAI | GOOGLE`.
- `AgentProfile.java` — panelist identity (name, perspective, lens).
- `StakeholderProfile.java` — human user profile (role, KPIs, focus area).
- `ConversationTurn.java` — record of one phase turn persisted to JSONL.
- `DebateListener.java` — event callbacks into the GUI.

### Persistence stores (all Gson-backed)
- `ProfileLibrary.java` — one directory per named profile set.
- `AgentProfileLibrary.java` — reusable agent personas.
- `PromptTemplate.java` / `PromptTemplateLibrary.java` — editable
  reaction + synthesis instruction blocks.
- `WorkflowStore.java` / `WorkflowDefinition.java` — user-defined
  agentic workflows.
- `ButtonStore.java` — ~50 built-in Executive Suite buttons + user-added.
- `OperationalFeedStore.java` / `OperationalFeedItem.java` — calendar.
- `RelationshipStore.java` / `Relationship.java` — partners, sponsors,
  advisors, vendors.
- `InitiativeStore.java` / `Initiative.java` — active projects.

### Attachments (PR #27)
- `ContextAttachment.java` — sealed interface.
- `FileAttachment.java` — local-path + MIME + display name.
- `FileUploader.java` — three provider dialects behind one
  `ensureUploaded(...)` method; in-memory cache.
- `AttachmentsDialog.java` — minimal Swing UI for attaching files.

### Organizational context (pillar #1 seed code)
- `OrganizationContext.java` — the ten-field org state (priorities,
  initiatives, deadlines, metrics, blockers, decisions, tone…), each
  wrapped in `ContextEntry` metadata.
- `ContextEntry.java` — value + `lastUpdated + source + confidence +
  status + computeFreshness()`.
- `Freshness.java` — enum `FRESH | AGING | STALE | NEEDS_CONFIRMATION`.
- `ContextStatus.java` — enum `APPROVED | PROVISIONAL | PENDING_REVIEW
  | ARCHIVED`.
- `ContextController.java` — toggles for every context layer;
  PromptBuilder consults it.
- `ContextSource.java` / `LocalContextSource.java` — backend interface
  + local JSON impl. Future cloud-context will add another impl.
- `ReconciliationService.java` — classifies proposed changes as
  `SAFE_AUTO` or `APPROVAL_REQUIRED` (`MergeDecision.java` enum).
- `ProposedChange.java` — record consumed by the reconciler.
- `ContextChangeLog.java` — JSONL audit trail of every mutation.
- `ContextPreset.java` — pre-filled templates for common scenarios.
- `DailyContextUpdateFunction.java` — scheduled agentic refresh.

### Agentic task framework
- `AgenticTask.java` — the interface every task implements.
- `AgenticTaskContext.java` — services passed into `execute()`.
- `AgenticTaskRegistry.java` — list + lookup of registered tasks.
- `AgenticRoutinesPanel.java` — the third view; sidebar of tasks.
- `TaskContext.java` — per-task prompt metadata (follow-ups + style).
- Concrete tasks: `UserWorkflowTask`, `StartYourDayTask`,
  `OutboundMessagesTask`, `MeetingPrepTask`, `StakeholderBriefingTask`,
  `WeeklyReportTask`, `InitiativeReviewTask`, `ContextRefreshTask`.

### Button surface
- `ButtonPanel.java` — grid of category-coloured buttons.
- `SuiteButton.java` — one button = one task template.
- `CategoryColorMap.java` — stable category → Color mapping.

### Audit
- `ApiRequestLog.java` — JSONL log of every provider call.
- `ApiRequestViewerDialog.java` — tabular viewer with per-row detail.

## Tier 3 — reference material

These are Swing construction code or small value types. Read them
only when touching that specific surface.

Dialogs: `ConfigEditorDialog`, `FirstLaunchSetupDialog`,
`ProfileSelectorDialog`, `ProfileSetEditorDialog`,
`AgentProfileEditorDialog`, `ButtonCreatorDialog`,
`ButtonCreationAssistantDialog`, `ContextControlDialog`,
`ContextUpdateDialog`, `WorkflowEditorDialog`,
`TaskQuestionDialog`, `OperationalFeedDialog`.

Small value types: `Recommendation`, `RecommendationEngine`,
`IconLoader`, `MergeDecision`.
