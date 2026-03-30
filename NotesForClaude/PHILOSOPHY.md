# AI Collaboration Platform — Development Philosophy

## How We Write Code

**Simple. Teachable. Commented.**

Every file in this project follows one rule: if a team member who didn't write
it can't read it and understand it in one sitting, it's too complex.

We are four university students with varying Java experience. The code we write
must serve two audiences simultaneously — the compiler and ourselves. That means:

- **No clever tricks.** If there's a simple way and a clever way, we pick simple.
- **Comments explain WHY, not just WHAT.** Every method gets a block comment
  explaining its purpose, why it exists, and how it fits into the bigger picture.
  Not `// increment counter` but `// Track API calls so the user stays aware of
  cost — each debate cycle makes 7 calls at ~$0.01-0.05 each.`
- **One concept per file.** Each Java class does one thing. If you can't describe
  what a class does in one sentence, it's doing too much.
- **Learn by pattern.** The three API clients (AnthropicClient, OpenAiClient,
  GeminiClient) are intentionally repetitive. They follow the same structure with
  small differences. Reading one teaches you all three. This repetition is
  deliberate — it's how you see that every API follows the same build-send-parse
  pattern, just with different details.

## Why We're Breaking Up Main.java

Our working prototype is a single 1,080-line Main.java. It works. All three
models respond, react, and synthesize. But it's become a bottleneck — only Grant
can safely modify it because only Grant knows where everything lives.

The refactor isn't about making the code "better" in the abstract. It's about
making it **possible for four people to work on it at the same time** without
stepping on each other. Each file becomes a unit of ownership:

```
ai-collab/
├── pom.xml                      ← Maven config + dependencies
└── src/main/java/collab/
    ├── Main.java                ← Entry point, CLI loop, hotseat selection
    ├── Orchestrator.java        ← Runs the 3-phase debate cycle
    ├── LlmClient.java           ← Interface (contract every model must follow)
    ├── AnthropicClient.java     ← Claude API calls
    ├── OpenAiClient.java        ← GPT API calls
    ├── GeminiClient.java        ← Gemini API calls
    └── ConversationContext.java ← Stores all prompts + responses for the session
```

### What each file does (one sentence each):

- **Main.java** reads user input, manages the hotseat menu, and calls the
  Orchestrator — it's the front door, not the brain.
- **Orchestrator.java** runs the 3-phase debate (independent → reaction →
  synthesis) by calling LlmClients and building prompts — this is the brain.
- **LlmClient.java** is an interface with one method: `sendMessage(prompt) →
  response` — it's the contract that every AI model must follow.
- **AnthropicClient.java** implements LlmClient for Claude's API — specific
  URL, headers (x-api-key), and JSON format.
- **OpenAiClient.java** implements LlmClient for GPT's API — Bearer token auth,
  max_completion_tokens parameter.
- **GeminiClient.java** implements LlmClient for Gemini's API — key in URL,
  different JSON structure (contents/parts instead of messages).
- **ConversationContext.java** holds every prompt and response from the session
  so the panel can reference previous cycles.

### Why LlmClient matters:

This interface is three lines of code but it's the design backbone. It means:
- Adding a new model (Llama, Mistral, your own) = one new file that implements
  the same interface. No changes to Orchestrator.
- Testing without API calls = create a MockClient that returns canned responses.
  No money spent, no network needed.
- Your professor sees polymorphism used for a real purpose, not a textbook
  exercise.

## The Onion Model (How We Give AI Context)

Every API call carries three layers of context, innermost to outermost:

1. **Team context** — shared by all agents: "You are one of three AI
   collaborators helping a student team build their final project."
2. **Agent identity** — unique per model: Claude focuses on architecture and
   quality, GPT on ideas and possibilities, Gemini on execution and delivery.
   Equal partners, no hierarchy.
3. **Team member profile** — who's in the hotseat: Grant the project lead gets
   architectural guidance; a teammate who's onboarding gets step-by-step help.

This isn't framework magic. It's just string concatenation — we prepend context
to the prompt before sending it. The AI reads the context and adjusts. Simple
mechanism, powerful effect.

## What We Don't Do

- **No external frameworks** until we understand what they replace. We built HTTP
  calls with Java's built-in HttpClient before considering Spring Boot. We
  parsed JSON by hand before adding Gson/Jackson. Understanding the manual way
  first means we know what the library is doing for us.
- **No features before foundation.** The refactor into separate classes happens
  before we add conversation memory, GUI, or any new capability. Building on a
  shaky foundation wastes more time than pausing to stabilize.
- **No code without comments.** If you write a method, you explain it. This is
  non-negotiable. The comments are as important as the code — they're how your
  teammates learn, how you remember what you did in three weeks, and how your
  professor evaluates your understanding.
