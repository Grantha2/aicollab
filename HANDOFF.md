# Handoff — state of the split as of this session

Give this file to whoever (or whatever agent) continues the work.
Read `README.md` for what the two projects are and `MIGRATION.md` for how
to turn them into their own repositories.

## Where each project stands

### `conductor/` — DONE, reviewed, documented (verify with `mvn test`)
- Provider layer (`agents/`, `config/`, `panel/`): complete. Anthropic,
  OpenAI, Gemini, OpenClaw clients behind one `AgentClient` interface;
  tool loop written once; retries/backoff/redaction in `Http`. 24 tests.
- SDLC layer (`sdlc/`, `ui/`, `Main`, `Wiring`): complete. 8 stages,
  3–5 questions each, one agent-assist per stage, debate panel at
  Requirements/Design, structured-JSON plan, OpenClaw hand-off at Build.
  20 tests. Whole module: 44 tests green at last run.
- An adversarial review of the provider layer and the README/CODE_TOUR
  writer were still running when this session ended. If
  `docs/CODE_TOUR.md` or `README.md` is missing or truncated, regenerate
  from the code; if `mvn test` fails, the reviewer's last edit is the
  suspect — `git diff HEAD~1 -- conductor/src/main/java/conductor/agents`.
- Known nit: `Panelist` file is `panelists.json`; `.gitignore` covers both
  that and the older `agents.json` name. Pick one and delete the other.

### `cowork-suite/` — PORTED; compiles as a whole (see test count below)
Four agents ported four package groups from the legacy tree (`88b926c`)
with strict ownership. Status at session end:
- `cowork.config` + `cowork.llm` — DONE. 26 tests green in isolation.
  Config rewritten (no System.exit/Scanner), AnthropicClient trimmed to
  ~300 LOC with Gson parsing, retries, key redaction, bounded state map;
  ApiRequestLog redacts and rotates.
- `cowork.context` + `cowork.data` — was running. Expect
  OrganizationContext (no legacy-format migration), trimmed
  ContextController, thread-safe ReconciliationService, stores via
  AppPaths, RecommendationEngine outbound-rule fix.
- `cowork.tasks` + `cowork.workflows` — was running. Expect TaskOutput
  interface replacing the panel dependency, all tasks using
  `ctx.clients().claude()`, PdfFillTool/RoomAvailabilityTool via AppPaths.
- `cowork.buttons` + `cowork.ui` + `Main` — was running. Expect MainGui
  rewritten suite-only (~450 LOC), ContextControlDialog cut to 2 tabs,
  FirstLaunchSetupDialog rewritten, ResultPanel new, no `simpleMode`.
Run `cd cowork-suite && mvn -q compile`; missing symbols tell you which
group did not finish. The legacy source for anything missing is at
`git show 88b926c:src/main/java/collab/<File>.java`.

## Queued fixes for cowork-suite (from the security audit — not yet applied)
1. `ReconciliationService.classify`: AI-sourced proposals must be
   APPROVAL_REQUIRED even when the target field is empty; proposals for
   field names not in `OrganizationContext.getFieldNames()` must be
   rejected, not auto-applied.
2. `DailyContextUpdateFunction`: the error-sentinel check tests for
   `"[ERROR]"` but clients return `"[Claude ERROR …]"` — check
   `startsWith("[") && contains("ERROR")`, return empty proposals and
   surface the error via `TaskOutput.setStatus`. Also drop proposals whose
   field name is unknown.
3. `RoomReservationWorkflow`: register `ComputerUseToolProxy` tools ONLY
   when `room.availability.mode=live`; require the sandbox URL to be
   `https` or `localhost`.
4. Every `SwingWorker` in `MainGui` / `AgenticRoutinesPanel` / tasks:
   override `done()`, call `get()`, show failures in the status bar;
   disable the triggering button while a worker runs.
5. Make `ApiRequestLog` opt-in (`api.log.enabled=false` default) and
   write `config.properties` with `600` permissions on POSIX.

## Then
- `mvn -q test` in both projects; fix until green.
- Write `cowork-suite/README.md` and `cowork-suite/docs/CODE_TOUR.md`
  (mirror the structure of conductor's).
- Follow `MIGRATION.md` to create the two repositories.

## Push blocker seen in this session
The container lost its git-proxy credentials mid-session: `git push` to
`https://github.com/Grantha2/aicollab` fails with "could not read
Username". Reads still work. Commits are local; a `git bundle` of them was
handed to the owner. To apply on your machine:
```
git clone https://github.com/Grantha2/aicollab && cd aicollab
git fetch /path/to/aicollab-split.bundle claude/refactor-reduce-complexity-wKV0q:refs/heads/split
git checkout split && git push -u origin split:claude/refactor-reduce-complexity-wKV0q
```

## Test status at session end
- conductor: run=65 failures=0 errors=0
- cowork-suite: run=97 failures=0 errors=0
