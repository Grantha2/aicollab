# 4. Persistence and resume

No database, no server. Everything persists as JSON or JSONL on the
local filesystem. This is deliberate: the app is a Swing desktop
binary, and introducing SQL would be more weight than its actual
requirements deserve.

## Where files live

| Directory | Content | Format |
|-----------|---------|--------|
| `sessions/session-<ISO-timestamp>.jsonl` | One file per debate session; append-only log of turns + syntheses | JSONL |
| `profiles/<name>/profile-set.json` | Named panelist + stakeholder + team-context bundles | JSON |
| `agent_profiles/<name>.json` | Reusable single-agent personas | JSON |
| `templates/<name>/prompt-template.json` | Editable Phase 2 / Phase 3 instruction blocks | JSON |
| `workflows/<name>.json` | User-defined agentic workflows | JSON |
| `buttons.json` | Executive Suite button catalog (~50 built-ins + user-defined) | JSON |
| `org_context.json` | `OrganizationContext` — live org state with per-field metadata | JSON |
| `relationships.json` | `RelationshipStore` | JSON |
| `initiatives.json` | `InitiativeStore` | JSON |
| `operational_feeds.json` | Meetings / deadlines / tasks | JSON |
| `api_request_log.jsonl` | Audit trail of every LLM call's request body | JSONL |
| `config.properties` | API keys + tunables. Gitignored. | Java properties |

## The session resume path

`Main.java:337-349` is the entire resume mechanic. On CLI start the
user picks "Resume existing session", a file is selected, and the
code calls:

```java
List<ConversationTurn> turns = store.loadTurns(selectedFile);
for (ConversationTurn turn : turns) context.addTurn(turn);
List<String> syntheses = store.loadSyntheses(selectedFile);
for (String synthesis : syntheses) context.addSynthesis(synthesis);
```

That is it. `SessionStore.loadTurns` and `loadSyntheses`
(`SessionStore.java:82-109`) scan the JSONL file line by line,
filter by `type` ("turn" vs "synthesis"), and return typed records.
`ConversationContext.addSynthesis` is idempotent and will trim the
oldest cycles if total history exceeds the character budget
(`ConversationContext.java:149-157`). Nothing else is required.

## Why custom JSON in SessionStore, Gson elsewhere?

`SessionStore` uses a hand-rolled JSON escaper and string extractor
(`SessionStore.java:156-221`). Everywhere else — `ProfileLibrary`,
`ButtonStore`, `OrganizationContext` — uses Gson. The reason is
append-only safety: JSONL means we can safely append a single line
without round-tripping the whole file through a library, which makes
an interrupted write (Ctrl-C, process crash) leave a truncated last
line at worst — never a corrupted JSON document. The custom
escaper avoids pulling Gson into the hot path. This is the single
place in the codebase where the "no extra dependency" rule wins
against consistency.

## Provider file IDs are intentionally NOT persisted

`FileUploader` caches uploaded file IDs in memory only
(`FileUploader.java:36-53`). They expire (Anthropic 30d, Gemini 48h)
and replaying a stale ID after a restart would silently drop the
attachment. Re-uploading on cold start is cheap and correct.

## What the file library (PR #30) will add

An `AttachmentLibraryStore` following the `ProfileLibrary` pattern —
one JSON file under `library/` per named reusable attachment. Pre-upload
IDs stay in the in-memory cache; only the local path + display name +
MIME type live on disk. Same JSONL/Gson philosophy as everything else.
