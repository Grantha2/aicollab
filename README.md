# AI Collab

A Java Swing desktop app where **Claude, GPT, and Gemini debate the same prompt**. Each model answers a user's question independently, then reads the other two models' responses and reacts — agreeing, challenging, or refining its own answer — for a configurable number of rounds.

Built as a final project to explore multi-model orchestration: routing the same request to three providers, normalizing their responses into a shared conversation format, and giving each model visibility into what the others said.

## How it works

1. You ask a question through the GUI.
2. `Maestro` sends the prompt independently to `AnthropicClient`, an OpenAI client, and `GeminiClient` behind a common `LlmClient` interface.
3. Each model's response is added to a shared `ConversationContext`.
4. On each debate round, every model re-prompts with the others' latest responses included, and reacts.
5. Results render in the Swing UI, organized by round.

## Run it

```bash
mvn compile exec:java
```

On first launch it prompts for API keys (Anthropic, OpenAI, Gemini) and writes them to a local, git-ignored `config.properties`. See `config.properties.example` for the keys needed and where to get them.

## Stack

Java · Maven · Swing (FlatLaf) · Anthropic / OpenAI / Gemini APIs · Gson

## Status

Active — the button-driven task layer originally built here was split out into a standalone project, [`cowork-suite`](https://github.com/grant-hauskins/cowork-suite).
