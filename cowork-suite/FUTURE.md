# Cowork Suite — Future Directions

Ideas for contributors. The suite already has a button board, seven
built-in agentic routines, an organisational context store with freshness
tracking, a reconciliation queue for AI-proposed changes, tools/MCP
plumbing, and a room-reservation workflow. This file is about where it
goes next. Nothing below is built.

## Vision in one paragraph

Cowork Suite is a one-click AI desk for organisational leaders who are
not engineers. Buttons capture intent through forms; the suite assembles
the prompt, keeps the organisation's shared context current, and runs
routines on a schedule. The next step is to make every button *agentic*:
a button becomes an agent with tools (read context, search the feed,
propose an update, draft a message) that works through a task and asks
for approval before anything leaves the building.

## Near-term (each a weekend)

1. **Buttons become agents.** Give `SuiteButton` an optional `tools` list
   and route it through the tool loop that already exists in
   `AnthropicClient` / `ToolExecutor`. Start with three read-only tools:
   `read_context(field)`, `search_feed(query, days)`,
   `search_initiatives(query)`. Approval-gated writes come after.
2. **Multi-provider routing.** Agentic tasks hard-code Claude. Add a
   `provider` field to `SuiteButton` and `WorkflowDefinition`
   (`claude | gpt | gemini | openclaw | auto`) and pick the `LlmClient`
   from a small registry. `LlmClient` already makes this possible.
3. **OpenClaw as a provider.** OpenClaw's gateway speaks the OpenAI wire
   format at `http://localhost:18789/v1/chat/completions` with
   `model: "openclaw/<agentId>"`. An `OpenClawClient` is ~60 lines that
   delegates to `OpenAiClient` with a different base URL and token. This
   lets a button hand a task to a self-hosted agent.
4. **Redact the audit log.** `ApiRequestLog` persists full request bodies
   to JSONL. Add a redaction pass (keys, emails, phone numbers) before
   write, and a retention cap (last N days).
5. **First real tests.** `ReconciliationService.decide`, `ContextEntry`
   freshness math, `ButtonStore` round-trip, `TaskContext.buildTaskBlock`.
   A salvaged `SessionStoreTest` from an old branch shows the pattern.

## Medium-term (a few weeks)

6. **Workflow execution engine.** `WorkflowDefinition` is a single prompt
   template. Add `steps[]` so step N's output feeds step N+1, with an
   approval gate any step can raise. Data model: `WorkflowStep{id, taskId,
   inputFrom[], paramOverrides}`; runtime: `WorkflowRun{stepStatuses,
   outputs, pausedAt}`.
7. **Workflow canvas.** A Swing panel that draws steps as nodes and data
   flow as edges; click a node for config and last output. Makes the
   routine catalogue legible instead of a flat sidebar.
8. **Agent monitoring.** `AgentRunLog{workflowId, runId, start, end,
   status, tokens, apiCalls, steps[], error}` persisted per run, plus a
   dashboard panel: what ran, what failed, what it cost.
9. **Shared cloud context.** Replace `LocalContextSource` with a cloud
   implementation behind the existing `ContextSource` interface
   (API Gateway + Lambda + DynamoDB was prototyped on an old branch —
   see `archive/cloud-context-lambda` tag). Feature-flag it:
   `context.source=local|cloud`.
10. **Role-based access.** An old branch prototyped `OfficerRole`,
    `FunctionalArea`, `UserSession`, `Contribution` for a multi-user
    officer platform (`archive/rbac` tag). Revisit once cloud context
    exists — roles without shared state don't mean much.

## Long-term (needs a design conversation first)

11. **Cross-user alignment agent.** Once context is shared, a periodic
    agent compares each user's stated priorities/risks and surfaces
    misalignments as high-urgency `Recommendation`s. Prompt sketch lived
    in the old ROADMAP; keep it in `docs/alignment-agent.md`.
12. **Template sharing.** Export/import button definitions; with cloud
    context, a shared button library per organisation.
13. **Web UI.** The stores and services have no Swing dependency; a thin
    HTTP layer would let a browser reuse them.

## Principles to keep

- **Forms, not prompts.** Users pick a button and answer questions; the
  suite writes the prompt.
- **Approval before side effects.** Anything that writes context, sends a
  message, or fills a form pauses for a human. `ReconciliationService`
  is the pattern.
- **Category = colour = grouping.** One concept drives the button board.
- **One JSON file per concern.** No generic store abstraction.
- **No secrets in code, logs, or JSONL.**
