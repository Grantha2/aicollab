# AI Collaboration Platform — Product Roadmap v2

## What This Application Is

A decision-support tool that helps organizational leaders interact with AI faster and more effectively than visiting providers independently. Three capabilities make this possible:

1. **Shared Cloud Context** — team-wide organizational memory that keeps AI grounded in reality
2. **Task Templates with Forms** — one-click prompting with structured intake, replacing manual typing
3. **Agentic Workflows at Scale** — deploy, visualize, and manage AI-powered automations from a single pane

---

## Current State (What Exists Today)

### Working
- 3-phase debate cycle (independent → reaction → synthesis) across Claude, GPT, Gemini
- Swing GUI with Executive Suite (button board), Debate view, Agentic Routines view
- Profile system: agent identities, stakeholder profiles, team context (persisted locally as JSON)
- Organization context with freshness tracking, per-field TTLs, ContextEntry metadata
- Reconciliation service for safe AI-proposed context updates (auto-apply vs approval queue)
- Task template buttons with follow-up question forms, style instructions, simple/debate toggle
- 7 built-in agentic tasks (Start Your Day, Outbound Messages, Context Refresh, Meeting Prep, Initiative Review, Weekly Report, Stakeholder Briefing)
- User-defined workflows via WorkflowEditorDialog (prompt template, context layer selection, output format)
- Recommendation engine surfacing next-best-actions based on freshness, feeds, and change log
- Session persistence via append-only JSONL
- Operational feed store (events, meetings, deadlines, tasks)
- Relationship and initiative structured data stores
- Context change log (audit trail)

### Not Yet Built
- Cloud sync for organization context (currently local JSON only)
- Multi-user access / shared context across team members
- Cloud-based agent that detects cross-user misalignment
- Visual workflow builder / orchestration canvas
- Agent deployment management (create, monitor, schedule agents)
- Provider-agnostic agent execution (currently Claude-only for agentic tasks)
- Web-based UI (currently Java Swing desktop only)

---

## Pillar 1: Shared Cloud Context

**Goal:** Every team member works from the same organizational truth. Context updates propagate instantly. Staleness is detected and resolved automatically.

### 1A. Cloud Context Backend (Current Priority)

**What:** Replace `LocalContextSource` with `AwsContextSource` behind the existing `ContextSource` interface.

**Architecture:**
- API Gateway + Lambda + DynamoDB (or equivalent)
- `ContextSource.get()` → GET from cloud, cache locally
- `ContextSource.save()` → POST to cloud, invalidate cache
- Offline fallback: if cloud unreachable, use local cache, queue writes for sync

**Files to change:**
- Create `AwsContextSource.java` implementing `ContextSource`
- `ContextController.setContextSource()` — swap at startup based on config
- `Config.java` — add `context.source=local|cloud` and cloud endpoint URL
- `OrganizationContext` — no changes needed (already behind interface)

**Migration path:**
1. Ship `AwsContextSource` with a feature flag (`context.source=local` default)
2. Test with one user writing, another reading
3. Flip to `cloud` when stable

### 1B. Multi-User Context Sync

**What:** Multiple users read/write the same org context. Changes from User A appear for User B within seconds.

**Design decisions:**
- Last-write-wins at the field level (not document level) to minimize conflicts
- Each write includes `userId`, `timestamp`, `source` for audit
- Change log becomes shared (cloud-backed ContextChangeLog)
- Freshness checks respect the most recent writer, not just "my last edit"

### 1C. Cross-User Alignment Agent (Stub)

**What:** A long-running cloud agent that monitors context contributions from multiple users and detects when stakeholders are misaligned.

**How it works:**
- Each user's context updates flow into a shared stream (DynamoDB Stream or similar)
- Periodically (or on significant change), the agent:
  1. Pulls recent updates grouped by user
  2. Sends them to Claude with a prompt asking: "Do these users agree on priorities, initiatives, and risks? Where are they misaligned?"
  3. If misalignment detected → creates a `Recommendation` with urgency HIGH, linked to a "Resolve Misalignment" task
  4. The recommendation surfaces in every user's Agentic Routines sidebar

**Stub prompt for the alignment-detection agent:**

```
You are an organizational alignment monitor. You receive context updates from
multiple users within the same organization.

Your job:
1. Compare each user's stated priorities, initiative status assessments,
   risk evaluations, and pending decisions
2. Identify CONFLICTS — where User A says X is on-track but User B says
   it's blocked, or where priorities don't match
3. Identify GAPS — important context one user has that others are missing
4. Rate each misalignment: CRITICAL (blocks decisions), IMPORTANT (creates
   confusion), or MINOR (cosmetic/timing difference)

For each misalignment found, output:
- FIELD: which context field is misaligned
- USERS: who disagrees
- SUMMARY: what each user believes
- IMPACT: what goes wrong if this isn't resolved
- SUGGESTED RESOLUTION: who should talk to whom, and what question to answer

If all users are aligned, say so and note the confidence level.

=== USER UPDATES ===
{per_user_context_snapshots}
```

**Scope:** This agent is out of scope for the current PR. The stub prompt and architecture description above are sufficient for a developer to implement it once the cloud context backend (1A) ships.

---

## Pillar 2: Task Templates & Structured Prompting

**Goal:** Users select what they want to do from a menu of buttons. Forms capture intent. The system assembles the prompt — users never write raw prompts for routine tasks.

### Current State (Already Built)
- `SuiteButton` with `TASK_TEMPLATE` action type
- `TaskQuestionDialog` collects follow-up answers
- `TaskContext.buildTaskBlock()` assembles task + style + answers into prompt
- `ButtonCreationAssistantDialog` — AI-assisted button creation
- 60+ default task buttons across 12 categories (Leadership, Meetings, Finance, etc.)
- Simple mode (single API call) vs debate mode (full 3-phase cycle)
- Organization context auto-prepended when enabled

### What to Improve

**2A. Smarter Form Routing:**
- After a user answers follow-up questions, analyze answers to determine if simple or debate mode is better (currently hardcoded per button)
- Add conditional questions: if answer to Q1 is "budget request", show Q2 about amount; otherwise skip

**2B. Output History per Button:**
- Track which buttons a user runs most frequently
- Show "last run" timestamp and a link to the previous output
- Enable "re-run with same answers" for recurring tasks

**2C. Template Sharing:**
- Export/import button definitions as JSON
- When cloud context ships: shared button library across the organization

---

## Pillar 3: Agentic Workflows at Scale (Primary Focus)

**Goal:** Make it trivially easy to deploy, visualize, monitor, and manage AI agents — starting with Claude's native agent capabilities, expanding to multi-provider orchestration.

This is the platform's highest value-add. Everything below is ordered by implementation priority.

### 3A. Workflow Visualization Canvas

**What:** A visual view where users see their workflows as connected nodes, not just a list of tasks in a sidebar.

**Why:** The current Agentic Routines view is a flat sidebar of task buttons + context health checkboxes. Users can't see how workflows connect, what triggers what, or where data flows. A canvas makes the system legible.

**Design:**

```
┌─────────────────────────────────────────────────────────┐
│ Workflow Canvas                              [+ New]    │
│                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Trigger  │───▶│ Context  │───▶│ Generate │          │
│  │ Daily 9am│    │ Refresh  │    │ Brief    │          │
│  └──────────┘    └──────────┘    └──────────┘          │
│                       │                │                │
│                       ▼                ▼                │
│                  ┌──────────┐    ┌──────────┐          │
│                  │ Approval │    │ Distribute│          │
│                  │ Queue    │    │ via Email │          │
│                  └──────────┘    └──────────┘          │
│                                                         │
│  Status: Last run 2026-04-11 09:00 • 3/4 steps OK      │
└─────────────────────────────────────────────────────────┘
```

**Implementation approach:**
- New `WorkflowCanvasPanel.java` — a custom Swing `JPanel` with painted nodes and edges
- Each node = one `AgenticTask` or `WorkflowDefinition` step
- Edges = data flow (output of step N feeds input of step N+1)
- Click a node → show its config, last output, run history
- Right-click → edit, disable, delete, duplicate
- Drag from one node's output port to another's input port to connect

**Data model addition:**
```java
public class WorkflowStep {
    String id;
    String taskId;           // references AgenticTask or WorkflowDefinition
    String label;
    int x, y;                // canvas position
    List<String> inputFrom;  // step IDs that feed into this step
    Map<String, String> paramOverrides; // override default task params
}
```

Add `List<WorkflowStep> steps` to `WorkflowDefinition` (currently it's a single-prompt template — this extends it to multi-step).

### 3B. Claude Agent Deployment via Tool Use

**What:** Integrate Claude's native tool-use / agentic capabilities so workflows can execute multi-step reasoning with tools, not just single prompt→response calls.

**Why:** Current agentic tasks are single-shot: build prompt → call Claude → display output. Real agentic value comes from Claude using tools in a loop: search context → identify gap → propose update → get approval → write back → confirm.

**Architecture:**

```java
// New: AgentExecutor.java — runs a Claude agent with tools
public class AgentExecutor {
    private final LlmClient client;
    private final List<ToolDefinition> tools;
    private final int maxIterations;

    public AgentResult execute(String systemPrompt, String userMessage) {
        // Agentic loop:
        // 1. Send messages + tool definitions to Claude
        // 2. If Claude returns tool_use → execute the tool locally
        // 3. Feed tool_result back to Claude
        // 4. Repeat until Claude returns final text or maxIterations hit
    }
}
```

**Tool definitions to build first:**
- `read_context(field_name)` → returns current org context field value + freshness
- `update_context(field_name, value, reason)` → proposes change via ReconciliationService
- `search_feed(query, days)` → searches operational feed items
- `search_relationships(query)` → searches relationship store
- `search_initiatives(query)` → searches initiative store
- `create_feed_item(title, type, date, ...)` → adds to operational feed
- `send_notification(recipient, message)` → stub for future email/Slack integration

**Anthropic API integration:**
- Use the `tools` parameter in the messages API request body
- Parse `tool_use` content blocks from Claude's response
- Execute tool locally, return `tool_result` in the next request
- `AnthropicClient.sendMessage(LlmRequest)` needs to support tool definitions and the agentic loop

**Migration:** Existing single-shot tasks (`ContextRefreshTask`, `StartYourDayTask`, etc.) continue working unchanged. New agentic tasks opt into `AgentExecutor` by providing tool definitions.

### 3C. Workflow Execution Engine

**What:** Execute multi-step workflows where each step's output feeds the next step's input, with error handling, retries, and human-in-the-loop approval gates.

**Current gap:** `UserWorkflowTask` runs a single prompt. There's no concept of "step 1 output becomes step 2 input."

**Design:**

```java
public class WorkflowEngine {
    public WorkflowRun execute(WorkflowDefinition workflow, AgenticTaskContext ctx) {
        WorkflowRun run = new WorkflowRun(workflow.getId());

        for (WorkflowStep step : workflow.getSteps()) {
            StepResult result = executeStep(step, run, ctx);

            if (result.requiresApproval()) {
                run.pauseAt(step.getId());
                return run; // UI shows approval prompt, resumes on user action
            }

            if (result.failed()) {
                run.fail(step.getId(), result.error());
                return run;
            }

            run.complete(step.getId(), result.output());
        }

        run.markComplete();
        return run;
    }
}
```

**Key concepts:**
- `WorkflowRun` — tracks execution state of a running workflow (step statuses, outputs, timing)
- Approval gates — any step can pause execution and wait for human confirmation
- Error recovery — failed steps can be retried or skipped
- Output passing — each step's output is available to subsequent steps via `{step:step_id}` placeholders

### 3D. Agent Monitoring Dashboard

**What:** A view showing all active/scheduled agents, their last run status, execution history, and resource usage.

**Why:** As users deploy more workflows, they need visibility into what's running, what failed, and what's scheduled.

**Components:**
- Agent list with status indicators (running, idle, failed, scheduled)
- Execution log: timestamp, duration, tokens used, outcome
- Alert for failed or stuck agents
- Cost tracking: estimated API cost per workflow per day/week

**Data model:**
```java
public class AgentRunLog {
    String workflowId;
    String runId;
    Instant startTime;
    Instant endTime;
    String status;          // "running", "completed", "failed", "paused"
    int totalTokensUsed;
    int apiCallsMade;
    List<StepLog> steps;
    String errorMessage;    // null if successful
}
```

### 3E. Multi-Provider Agent Execution

**What:** Workflows can route steps to different AI providers based on the task (Claude for reasoning, GPT for creative, Gemini for data analysis).

**Current state:** All agentic tasks hardcode `AnthropicClient`. The `LlmClient` interface already supports any provider.

**Changes:**
- `WorkflowStep` gets a `provider` field: `"claude"`, `"gpt"`, `"gemini"`, or `"auto"`
- `"auto"` routes based on step type: analysis → Claude, creative → GPT, execution → Gemini (matches existing agent profiles)
- `AgentExecutor` accepts an `LlmClient` parameter, not hardcoded provider
- Tool-use agentic loops initially Claude-only (other providers' tool-use APIs added incrementally)

### 3F. Guided Workflow Suggestions

**What:** When a user creates a new workflow, the system suggests similar existing workflows and recommends complementary automations.

**Example:** User creates "Weekly Board Report" workflow. System suggests:
- "You might also want a 'Pre-Board-Meeting Prep' workflow that runs 2 days before"
- "Consider adding an 'Initiative Review' step before the report generation"
- "Similar workflow 'Weekly Team Report' exists — would you like to fork it?"

**Implementation:** Use the existing `RecommendationEngine` pattern. When a workflow is created or edited, analyze its prompt template and context layers against the existing workflow library.

---

## Implementation Sequence

### Phase 1: Cloud Foundation (Weeks 1–2)
1. `AwsContextSource.java` — cloud read/write behind existing interface
2. Config flag to toggle local vs cloud
3. Test with 2 users on same org context
4. Cloud-backed `ContextChangeLog`

### Phase 2: Agent Execution Layer (Weeks 2–3)
1. `AgentExecutor.java` — agentic loop with tool use
2. Core tool definitions (read_context, update_context, search_feed)
3. Upgrade `ContextRefreshTask` to use `AgentExecutor` (proof of concept)
4. `WorkflowStep` data model extension
5. `WorkflowEngine.java` — multi-step execution with output passing

### Phase 3: Visualization (Weeks 3–4)
1. `WorkflowCanvasPanel.java` — visual node-and-edge workflow display
2. Node rendering, edge painting, click/drag interaction
3. Wire canvas to `WorkflowDefinition.steps`
4. Execution status overlay (green/yellow/red per node)

### Phase 4: Monitoring & Multi-Provider (Weeks 4–5)
1. `AgentRunLog` and execution history persistence
2. Agent monitoring dashboard panel
3. Cost estimation per workflow
4. Multi-provider routing in `WorkflowStep`
5. Guided workflow suggestions

### Phase 5: Alignment Agent (Post-Cloud Stability)
1. Cloud event stream for context changes
2. Periodic alignment analysis via Claude
3. Misalignment recommendations surfaced per-user
4. Resolution workflow (flag → discuss → update → confirm)

---

## File Inventory (New Files Needed)

| File | Pillar | Purpose |
|---|---|---|
| `AwsContextSource.java` | 1 | Cloud context backend |
| `AgentExecutor.java` | 3 | Agentic loop with tool use |
| `ToolDefinition.java` | 3 | Tool schema for Claude tool use |
| `ToolRegistry.java` | 3 | Registry of available tools |
| `ContextTools.java` | 3 | Built-in tools (read/write context, search feeds) |
| `WorkflowStep.java` | 3 | Single step in a multi-step workflow |
| `WorkflowEngine.java` | 3 | Multi-step workflow execution |
| `WorkflowRun.java` | 3 | Execution state of a running workflow |
| `WorkflowCanvasPanel.java` | 3 | Visual workflow editor/viewer |
| `AgentRunLog.java` | 3 | Execution history record |
| `AgentMonitorPanel.java` | 3 | Dashboard for agent status/history |
| `AlignmentAgent.java` | 1 | Cross-user misalignment detection (stub) |

---

## Design Principles (Unchanged)

1. **Simple. Teachable. Commented.** — every new class follows one-concept-per-file
2. **No framework magic** — plain Java, Gson for JSON, constructor injection
3. **Interface-first** — `ContextSource`, `LlmClient`, `AgenticTask` are the extension points
4. **Context Layering Architecture** — team context + agent identity + stakeholder + org context + task context + history
5. **Category = Color = Grouping** — one concept drives all three in the button board
