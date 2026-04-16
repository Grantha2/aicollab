# 1. Architecture map

aicollab is organised into four layers. A request flows top-to-bottom;
persistence and context services sit to the side and are consulted by
every layer above them.

```
                      ┌────────────────────────────────────┐
  USER  ───────────▶  │  GUI LAYER                         │
                      │  MainGui.java, AgenticRoutinesPanel │
                      │  ButtonPanel, all *Dialog.java      │
                      └────────────────┬───────────────────┘
                                       │ user prompt + stakeholder
                                       ▼
                      ┌────────────────────────────────────┐
                      │  ORCHESTRATION                     │
                      │  Maestro.java — the 3-phase loop   │
                      │  PromptBuilder — assembles layers  │
                      └────────────────┬───────────────────┘
                                       │ LlmRequest (system + messages
                                       │            + attachments)
                                       ▼
                      ┌────────────────────────────────────┐
                      │  PROVIDER CLIENTS                  │
                      │  LlmClient (interface)             │
                      │    ├─ AnthropicClient              │
                      │    ├─ OpenAiClient                 │
                      │    └─ GeminiClient                 │
                      └────────────────┬───────────────────┘
                                       │ HTTP
                                       ▼
                                  [Provider APIs]

     Consulted by every layer above:
     ┌────────────────────────────────┐  ┌────────────────────────────┐
     │ CONTEXT / ORG STATE            │  │ PERSISTENCE                │
     │ OrganizationContext            │  │ SessionStore      (JSONL)  │
     │ ContextController, ContextEntry│  │ ProfileLibrary     (JSON)  │
     │ ReconciliationService          │  │ ButtonStore        (JSON)  │
     │ ContextChangeLog (audit)       │  │ WorkflowStore      (JSON)  │
     └────────────────────────────────┘  └────────────────────────────┘
```

## What lives in each layer

**GUI layer.** Swing `JFrame` with a `CardLayout` that switches between
three views (Executive Suite buttons, Debate & Conversation streams,
Agentic Routines) — `MainGui.java:14-16`. The GUI owns no model
state; it reads from stores and calls into orchestration.

**Orchestration.** `Maestro.java` runs the 3-phase cycle and doesn't know
or care which provider is behind each panelist — it only sees a list
of `LlmClient` (`Maestro.java:103-119`). `PromptBuilder` composes the
layered context prompt (`PromptBuilder.java:17-29`).

**Provider clients.** Three implementations of one interface
(`LlmClient.java:51`). Each client knows exactly one provider's JSON
dialect, auth header, and state-chaining mechanism. Two Claudes + one
GPT is a valid configuration; the panel size is not fixed at three
(`Maestro.java:115-117`).

**Context and persistence.** Gson + JSONL throughout. Sessions are
append-only JSONL so resume just replays the file
(`SessionStore.java:61-98`). Profile sets, workflow definitions,
buttons, organisational context — each has its own Gson-backed store
following the same pattern.

## The design insight worth repeating

The `LlmClient` interface is the hinge. Add a new provider → implement
the interface → pass it into `Maestro`. Nothing else changes. This is
also how we will wire tool-calling in PR #29: each client gains a tool
loop, but the interface stays the same.
