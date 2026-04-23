# aicollab — Code Tour

A single-sitting walk through the codebase. If you read this file
top-to-bottom you will understand every line of source in the project.

---

## The 30-second version

aicollab runs a three-phase debate between Claude, GPT, and Gemini over
one user question:

1. **Phase 1.** Each agent answers independently.
2. **Phase 2.** Each agent sees the others' answers and reacts.
3. **Phase 3.** The first agent (Claude by default) synthesises everything.

That loop is wrapped in a Swing chat window (or a CLI prompt loop).
Users edit the three agent identities via a menu, and paste any
organisation/stakeholder context into a single text box that is
prepended to every prompt.

The whole project is **10 Java files, ~640 lines of code**, plus Gson
and FlatLaf as its only dependencies.

---

## How to run

```
mvn compile exec:java                         # Swing GUI (default)
mvn compile exec:java -Dexec.args="--cli"     # CLI debate loop
```

First run: copy `config.properties.example` to `config.properties` and
fill in your API keys. The app will tell you if the file is missing or
a key is blank.

Persisted files (all gitignored):
- `config.properties` — API keys and model names.
- `agents.json`       — the three agent panelists (edited via **Settings → Agents...**).
- `context.txt`       — the free-form context blob (edited via **Settings → Context...**).

Delete any of these to reset to defaults.

---

## File map (10 files, ~640 LOC)

| File                     | LOC | One-sentence job                                          |
|--------------------------|----:|-----------------------------------------------------------|
| `Main.java`              |  ~60 | CLI entry, wires up the Maestro, runs the prompt loop.   |
| `MainGui.java`           | ~150 | Swing chat window + Agents / Context dialogs.            |
| `Maestro.java`           | ~110 | The 3-phase debate loop; also builds the prompts.         |
| `Agent.java`             |  ~55 | Record for one panelist; loads/saves `agents.json`.       |
| `Config.java`            |  ~55 | Loads `config.properties`; load/save helpers for context. |
| `LlmClient.java`         |   ~8 | Interface: `send(system, messages) → String`.             |
| `AnthropicClient.java`   |  ~60 | Calls Claude's Messages API.                              |
| `OpenAiClient.java`      |  ~60 | Calls OpenAI's Chat Completions API.                      |
| `GeminiClient.java`      |  ~70 | Calls Google's generateContent API.                       |
| `ChatMessage.java`       |   ~3 | Record: `(role, content)`.                                |

Nothing else exists in `src/main/java/collab/`.

---

## Reading order (30 minutes)

Read these in this order and you will see every moving part:

1. **`ChatMessage.java`** — the record every API call is built from.
2. **`LlmClient.java`** — the contract every provider implements.
3. **`AnthropicClient.java`** — one concrete client start-to-finish
   (the other two follow the same shape; skim them afterwards).
4. **`Agent.java`** — what an agent panelist is, plus JSON load/save.
5. **`Config.java`** — key loading and the two `context.txt` helpers.
6. **`Maestro.java`** — the 3-phase loop. Read `runDebate` top to bottom,
   then the three prompt-body helpers.
7. **`Main.java`** — CLI wiring. Short.
8. **`MainGui.java`** — Swing wiring. Its only two "non-chrome" methods
   are `showAgentsDialog` and `showContextDialog`.

If you only have five minutes, read `Maestro.runDebate` and one
client's `send` method.

---

## How a debate cycle actually runs

### Input
- `userPrompt` (from the Send button / CLI `SEND`).
- `agents` (3 entries loaded from `agents.json`, or defaults).
- `clients` (3 `LlmClient` instances matching the agents 1:1).
- `contextBlob` (the text from `context.txt`).
- `syntheses` (in-memory list of every prior cycle's synthesis).

### Per-agent system instruction (`Maestro.systemInstruction`)
```
=== CONTEXT ===
{contextBlob, if non-empty}

=== YOUR AGENT IDENTITY ===
Agent name: Claude
Perspective: Architecture & Quality
{lens text}

=== PRIOR CYCLE CONCLUSIONS ===      ← only if syntheses is non-empty
--- Cycle 1 ---
...
```

### Phase 1 (one call per agent)
User message is simply:
```
=== QUESTION ===
{userPrompt}
```
Each response is stored in both `phase1[i]` and `latest[i]`.

### Phase 2 (one call per agent, repeated `debateRounds` times)
User message names the agent's own identity and lists the *other*
agents' latest answers, then asks for a reaction.
After each round, the just-produced reactions become the new `latest`
for the next round.

### Phase 3 (one call; first client only)
User message is a big aggregate of Phase 1 + final reactions, asking
for points of agreement, key disagreements, missed insights, and a
concrete recommendation. Result is appended to `syntheses`.

### API call math
`1 + N + N × rounds`  where N = number of agents (3 by default).
With `debate.rounds=1`: **7 API calls per cycle**.

---

## The two UI dialogs

### Agents (Settings → Agents...)
A `JOptionPane` built from a `BoxLayout` of one titled box per agent.
Each box has three fields: Name, Perspective, Lens. OK writes back to
`agents.json` via `Agent.saveAll`.

Changes take effect on app restart — the `Maestro` holds a snapshot.

### Context (Settings → Context...)
A single `JTextArea` pre-filled with `Config.loadContext()`. OK writes
the text back to `context.txt`. Takes effect on the *next* cycle
(the Maestro reads `contextBlob` through its constructor — for a clean
implementation, restart after editing).

---

## Extending the system

**Add a fourth agent.** Open `agents.json` (or the Agents dialog),
append a fourth entry, and add a matching `LlmClient` to
`Main.buildMaestro`. The Maestro has no hardcoded panel size — it just
requires `clients.size() == agents.size() && agents.size() >= 2`.

**Swap in a different model.** The three `*Client.java` files each
implement `LlmClient`. Write your own class with the same one-method
contract and pass it to `new Maestro(clients, ...)`. Nothing else changes.

**Persist session history.** There is no session store. If you want one,
the single-row hook is `syntheses.add(synthesis)` at the end of
`Maestro.runDebate`. Write to a file there; load the file into
`syntheses` in the `Maestro` constructor.

**Add a tool / function-call path.** Not supported. The `LlmClient`
interface is deliberately text-in / text-out. If the team needs tools
later, they become an additional method on the interface, not a
modification to the existing one.

---

## Conventions

- **Error contract.** LLM client methods return an error-prefixed string
  (e.g. `"[Claude ERROR 401] ..."`) rather than throwing, so a single
  failure doesn't abort the whole debate. Look for `[Claude ERROR`,
  `[GPT ERROR`, or `[Gemini ERROR` prefixes in the transcript.
- **No global state.** Every runtime object is passed into its
  consumer's constructor. Nothing reads from `System.getenv` or
  singletons.
- **No streaming.** Every HTTP call is blocking and returns the full
  body. Makes the clients one state machine simpler.
- **Gson is the only JSON library** on the read side (responses are
  parsed with `JsonParser`). Requests are built with `JsonObject` /
  `JsonArray` — no manual escaping, no template strings.
