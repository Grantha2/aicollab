# aicollab — Code Tour

A guided walk through the Java source tree for reviewers, new contributors,
and students preparing to explain the project orally.

Each section below lives in its own file so readers (and rehearsing
presenters) can open them one at a time without scrolling past everything
else. Every section cites `file:line` so you can click through to the
source instead of trusting a paraphrase.

## Start here

| # | Section | When to read |
|---|---|---|
| 1 | [Architecture map](tour/01-architecture.md) | First. Gives you the four layers and one diagram. |
| 2 | [Reading order](tour/02-reading-order.md) | A 30-minute ordered walk through the core files. |
| 3 | [Debate call flow](tour/03-debate-flow.md) | The one trace that explains how a cycle actually runs. |
| 4 | [Persistence and resume](tour/04-persistence.md) | How state survives restarts (sessions, profiles). |
| 5 | [Attachments (PR #27)](tour/05-attachments.md) | How files ride along on an API call. |
| 6 | [File atlas](tour/06-file-atlas.md) | Tiered per-file reference for when you need it. |
| 7 | [Glossary & FAQ](tour/07-glossary-faq.md) | Terms to know; questions a professor is likely to ask. |
| 8 | [Change log](tour/08-changelog.md) | One-line entry per PR so you can trace what landed when. |

## How to run

```
mvn compile exec:java        # launches the Swing GUI (default)
mvn compile exec:java -Dexec.args="--cli"   # CLI debate loop (hotseat mode)
```

On first launch the app asks for your Anthropic, OpenAI, and Google API
keys and writes them to `config.properties`. It will not ask again.

## What this project is, in one paragraph

aicollab is a Java Swing desktop app that coordinates Claude, GPT, and
Gemini around a single user question in a three-phase debate cycle
(independent answers → cross-reaction → synthesis). Beyond the debate, it
is also an AI hub for student organization leaders: a library of task
buttons, a catalog of agentic routines (daily context refresh, meeting
prep, stakeholder briefings), an organizational-context store with
freshness tracking, and a reconciliation pipeline that gates AI-proposed
updates behind human approval. All three providers are hidden behind one
`LlmClient` interface (`LlmClient.java:51`) so the orchestrator never
knows which vendor it is talking to.

## Conventions used in the tour

- `file:line` — click to open at that line.
- **Tier 1 / Tier 2 / Tier 3** — how load-bearing a file is. Tier 1 is
  what you must be able to explain; Tier 3 is reference material.
- "Seed code" — a class present today that a future phase (RAG, MCP,
  cloud context) will extend rather than replace.
